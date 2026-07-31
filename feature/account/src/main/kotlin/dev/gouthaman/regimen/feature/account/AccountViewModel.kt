package dev.gouthaman.regimen.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.AuthException
import dev.gouthaman.regimen.domain.model.SecondaryDeviceReason
import dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason
import dev.gouthaman.regimen.domain.model.SyncReplaceException
import dev.gouthaman.regimen.domain.model.SyncStatus
import dev.gouthaman.regimen.domain.usecase.CancelPeriodicSyncUseCase
import dev.gouthaman.regimen.domain.usecase.ClaimPrimaryUseCase
import dev.gouthaman.regimen.domain.usecase.CloudWorkoutCountUseCase
import dev.gouthaman.regimen.domain.usecase.DeleteCloudDataUseCase
import dev.gouthaman.regimen.domain.usecase.EnsurePrimaryClaimedUseCase
import dev.gouthaman.regimen.domain.usecase.GetLastSyncStatusUseCase
import dev.gouthaman.regimen.domain.usecase.GetNextScheduledSyncAtUseCase
import dev.gouthaman.regimen.domain.usecase.GetSecondaryDeviceReasonUseCase
import dev.gouthaman.regimen.domain.usecase.LocalWorkoutCountUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveAccountStatusUseCase
import dev.gouthaman.regimen.domain.usecase.PullCloudDataUseCase
import dev.gouthaman.regimen.domain.usecase.SchedulePeriodicSyncUseCase
import dev.gouthaman.regimen.domain.usecase.SignInUseCase
import dev.gouthaman.regimen.domain.usecase.SignOutUseCase
import dev.gouthaman.regimen.domain.usecase.SyncNowUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which action, if any, currently has a request in flight - lets the UI show a spinner on
 * specifically the button that triggered it while disabling every other button. Requesting
 * Pull/Claim (fetching the confirmation counts) and actually running them share the same action
 * value - from the UI's perspective both phases are "this button is busy." */
enum class AccountAction { SIGN_IN, SIGN_OUT, DELETE_CLOUD_DATA, SYNC_NOW, PULL_CLOUD_DATA, CLAIM_PRIMARY }

/** The not-primary no-op shape [dev.gouthaman.regimen.domain.repository.SyncPushRepository.push]
 * returns - a no-op, not a real outcome, so it should never overwrite a genuinely persisted
 * "Synced at ..." with "Not yet synced" (same reasoning `SyncPushRunner` already applies to what
 * gets persisted, mirrored here for what gets shown in memory). */
private val NOT_PRIMARY_STATUS =
    SyncStatus(lastSyncedAt = null, isFullyUpToDate = false, lastError = null)

/** Populates a Pull/Claim confirmation dialog's count-based copy - always shown, never
 * conditional, so a fresh/empty device shows up as "0 workouts" and makes the wrong-order mistake
 * (Claim before Pull, on a reformatted device) visible before it happens. */
data class ReplaceConfirmation(val localWorkoutCount: Int, val cloudWorkoutCount: Int)

data class AccountUiState(
    val account: AuthAccount? = null,
    val isSignInAvailable: Boolean = false,
    val busyAction: AccountAction? = null,
    val errorReason: AuthErrorReason? = null,
    val syncStatus: SyncStatus? = null,
    val secondaryDeviceReason: SecondaryDeviceReason? = null,
    val replaceErrorReason: SyncReplaceErrorReason? = null,
    val pullConfirmation: ReplaceConfirmation? = null,
    val claimConfirmation: ReplaceConfirmation? = null,
    val nextScheduledSyncAt: Long? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    observeAccountStatus: ObserveAccountStatusUseCase,
    private val signInUseCase: SignInUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val deleteCloudDataUseCase: DeleteCloudDataUseCase,
    private val ensurePrimaryClaimedUseCase: EnsurePrimaryClaimedUseCase,
    private val schedulePeriodicSyncUseCase: SchedulePeriodicSyncUseCase,
    private val cancelPeriodicSyncUseCase: CancelPeriodicSyncUseCase,
    private val syncNowUseCase: SyncNowUseCase,
    private val getLastSyncStatusUseCase: GetLastSyncStatusUseCase,
    private val getSecondaryDeviceReasonUseCase: GetSecondaryDeviceReasonUseCase,
    private val pullCloudDataUseCase: PullCloudDataUseCase,
    private val claimPrimaryUseCase: ClaimPrimaryUseCase,
    private val localWorkoutCountUseCase: LocalWorkoutCountUseCase,
    private val cloudWorkoutCountUseCase: CloudWorkoutCountUseCase,
    private val getNextScheduledSyncAtUseCase: GetNextScheduledSyncAtUseCase,
) : ViewModel() {

    private val busyAction = MutableStateFlow<AccountAction?>(null)
    private val error = MutableStateFlow<AuthErrorReason?>(null)
    private val syncStatus = MutableStateFlow<SyncStatus?>(null)
    private val secondaryDeviceReason = MutableStateFlow<SecondaryDeviceReason?>(null)
    private val replaceError = MutableStateFlow<SyncReplaceErrorReason?>(null)
    private val pullConfirmation = MutableStateFlow<ReplaceConfirmation?>(null)
    private val claimConfirmation = MutableStateFlow<ReplaceConfirmation?>(null)
    private val nextScheduledSyncAt = MutableStateFlow<Long?>(null)

    init {
        // Restores the last run's outcome across app restarts - without this, syncStatus starts
        // at null ("Not yet synced") every time, even right after a successful sync, since it was
        // otherwise only ever held in this in-memory StateFlow.
        viewModelScope.launch { syncStatus.value = getLastSyncStatusUseCase() }
        refreshOnResume()
    }

    // Best-effort: may briefly race the fire-and-forget claim attempt right after a fresh sign-in,
    // showing the single-device experience a moment longer than necessary - never a false
    // positive. Not private - also called from the Composable on every screen resume, since this
    // device can be demoted to secondary at any point, not just around sign-in/init.
    fun refreshOnResume() {
        viewModelScope.launch {
            runCatching { getSecondaryDeviceReasonUseCase() }.onSuccess {
                secondaryDeviceReason.value = it
            }
        }
        viewModelScope.launch {
            runCatching { getNextScheduledSyncAtUseCase() }.onSuccess {
                nextScheduledSyncAt.value = it
            }
        }
    }

    val uiState: StateFlow<AccountUiState> = combine(
        observeAccountStatus(), busyAction, error, syncStatus,
        secondaryDeviceReason,
        replaceError,
        pullConfirmation,
        claimConfirmation,
        nextScheduledSyncAt,
    ) { values ->
        val account = values[0] as AuthAccount?
        val busy = values[1] as AccountAction?
        val errorReason = values[2] as AuthErrorReason?
        val status = values[3] as SyncStatus?
        val secondary = values[4] as SecondaryDeviceReason?
        val replaceErrorReason = values[5] as SyncReplaceErrorReason?
        val pullConfirm = values[6] as ReplaceConfirmation?
        val claimConfirm = values[7] as ReplaceConfirmation?
        val nextSync = values[8] as Long?
        AccountUiState(
            account = account,
            isSignInAvailable = signInUseCase.isAvailable,
            busyAction = busy,
            errorReason = errorReason,
            syncStatus = status,
            secondaryDeviceReason = secondary,
            replaceErrorReason = replaceErrorReason,
            pullConfirmation = pullConfirm,
            claimConfirmation = claimConfirm,
            nextScheduledSyncAt = nextSync,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun signIn() {
        if (busyAction.value != null) return
        viewModelScope.launch {
            busyAction.value = AccountAction.SIGN_IN
            error.value = null
            signInUseCase()
                .onSuccess {
                    claimPrimaryIfUnset()
                    schedulePeriodicSyncUseCase()
                }
                .onFailure { error.value = it.toReason() }
            busyAction.value = null
        }
    }

    // Best-effort: whether this device becomes primary is orthogonal to whether sign-in itself
    // succeeded, so a failure here (e.g. offline) doesn't surface as a sign-in error - it just
    // means the check runs again on the next successful sign-in/app-open.
    private fun claimPrimaryIfUnset() {
        viewModelScope.launch {
            runCatching { ensurePrimaryClaimedUseCase() }
            refreshOnResume()
        }
    }

    fun signOut() {
        if (busyAction.value != null) return
        viewModelScope.launch {
            busyAction.value = AccountAction.SIGN_OUT
            error.value = null
            signOutUseCase().onFailure { error.value = it.toReason() }
            cancelPeriodicSyncUseCase()
            // A dialog left over from this session shouldn't be able to show again for whoever
            // signs in next.
            secondaryDeviceReason.value = null
            replaceError.value = null
            pullConfirmation.value = null
            claimConfirmation.value = null
            busyAction.value = null
        }
    }

    fun deleteCloudData() {
        if (busyAction.value != null) return
        viewModelScope.launch {
            busyAction.value = AccountAction.DELETE_CLOUD_DATA
            error.value = null
            deleteCloudDataUseCase().onFailure { error.value = it.toReason() }
            busyAction.value = null
        }
    }

    /** Out-of-band run of the same incremental push the periodic job does - not destructive, so
     * unlike Pull/Claim it needs no confirmation, just the same busy-spinner/
     * disabled-while-in-flight treatment every other action here uses. The button itself is
     * hidden for secondary devices (see AccountScreen.kt), so the [NOT_PRIMARY_STATUS] guard
     * below is defense-in-depth, not the primary mechanism. */
    fun syncNow() {
        if (busyAction.value != null) return
        viewModelScope.launch {
            busyAction.value = AccountAction.SYNC_NOW
            val result = syncNowUseCase()
            if (result != NOT_PRIMARY_STATUS) syncStatus.value = result
            busyAction.value = null
        }
    }

    /** Fetches the counts a Pull confirmation dialog needs before showing it - a Firestore
     * `count()` round trip, so this is its own busy phase rather than happening synchronously. */
    fun requestPullCloudData() {
        if (busyAction.value != null) return
        viewModelScope.launch {
            busyAction.value = AccountAction.PULL_CLOUD_DATA
            replaceError.value = null
            pullConfirmation.value = ReplaceConfirmation(
                localWorkoutCount = localWorkoutCountUseCase(),
                cloudWorkoutCount = cloudWorkoutCountUseCase(),
            )
            busyAction.value = null
        }
    }

    fun dismissPullConfirmation() {
        pullConfirmation.value = null
    }

    /** A pull resets the local freshness watermark to match the cloud - which can resolve
     * [SecondaryDeviceReason.STALE_LOCAL_STATE] for a device that was already primary but had
     * stale local state, so [refreshOnResume] re-checks afterward the same way
     * [confirmClaimPrimary] does. Doesn't affect primary status itself either way, unlike Claim. */
    fun confirmPullCloudData() {
        pullConfirmation.value = null
        if (busyAction.value != null) return
        viewModelScope.launch {
            busyAction.value = AccountAction.PULL_CLOUD_DATA
            replaceError.value = null
            pullCloudDataUseCase()
                .onSuccess { refreshOnResume() }
                .onFailure { replaceError.value = it.toReplaceReason() }
            busyAction.value = null
        }
    }

    /** See [requestPullCloudData] - same two-phase shape. */
    fun requestClaimPrimary() {
        if (busyAction.value != null) return
        viewModelScope.launch {
            busyAction.value = AccountAction.CLAIM_PRIMARY
            replaceError.value = null
            claimConfirmation.value = ReplaceConfirmation(
                localWorkoutCount = localWorkoutCountUseCase(),
                cloudWorkoutCount = cloudWorkoutCountUseCase(),
            )
            busyAction.value = null
        }
    }

    fun dismissClaimConfirmation() {
        claimConfirmation.value = null
    }

    fun confirmClaimPrimary() {
        claimConfirmation.value = null
        if (busyAction.value != null) return
        viewModelScope.launch {
            busyAction.value = AccountAction.CLAIM_PRIMARY
            replaceError.value = null
            claimPrimaryUseCase()
                .onSuccess {
                    secondaryDeviceReason.value = null
                    // A device that was secondary already had its own periodic job self-cancel
                    // (it noticed it wasn't primary and stopped itself) - nothing else
                    // re-schedules one once it becomes primary again, so this is what actually
                    // restarts it. Idempotent (KEEP), safe to call regardless of whether one
                    // somehow already exists.
                    schedulePeriodicSyncUseCase()
                    refreshOnResume()
                }
                .onFailure { replaceError.value = it.toReplaceReason() }
            busyAction.value = null
        }
    }

    private fun Throwable.toReason(): AuthErrorReason =
        (this as? AuthException)?.reason ?: AuthErrorReason.UNKNOWN

    private fun Throwable.toReplaceReason(): SyncReplaceErrorReason =
        (this as? SyncReplaceException)?.reason ?: SyncReplaceErrorReason.UNKNOWN
}