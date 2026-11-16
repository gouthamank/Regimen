package dev.gouthaman.regimen.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.AuthException
import dev.gouthaman.regimen.domain.model.SecondaryDeviceReason
import dev.gouthaman.regimen.domain.model.SyncOperationProgress
import dev.gouthaman.regimen.domain.model.SyncOperationStep
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
import dev.gouthaman.regimen.domain.usecase.ReauthenticateUseCase
import dev.gouthaman.regimen.domain.usecase.SchedulePeriodicSyncUseCase
import dev.gouthaman.regimen.domain.usecase.SignInUseCase
import dev.gouthaman.regimen.domain.usecase.SignOutUseCase
import dev.gouthaman.regimen.domain.usecase.SyncNowUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which quick action, if any, currently has a request in flight - lets the UI show a spinner on
 * specifically the button that triggered it while disabling every other button. The three long
 * operations are tracked through [AccountUiState.runningOperation] instead; [DELETE_CLOUD_DATA]
 * only covers Delete's re-authentication step before the operation itself starts. */
enum class AccountAction { SIGN_IN, SIGN_OUT, DELETE_CLOUD_DATA, SYNC_NOW }

/** The three operations that replace or remove a whole data set, shown in a blocking progress
 * dialog while they run. */
enum class SyncOperation { PULL_CLOUD_DATA, CLAIM_PRIMARY, DELETE_CLOUD_DATA }

data class RunningOperation(val operation: SyncOperation, val progress: SyncOperationProgress)

/** The not-primary no-op shape [dev.gouthaman.regimen.domain.repository.SyncPushRepository.push]
 * returns - a no-op, not a real outcome, so it should never overwrite a genuinely persisted
 * "Synced at ..." with "Not yet synced" (same reasoning `SyncPushRunner` already applies to what
 * gets persisted, mirrored here for what gets shown in memory). */
private val NOT_PRIMARY_STATUS =
    SyncStatus(lastSyncedAt = null, isFullyUpToDate = false, lastError = null)

/** Populates a Pull/Claim confirmation dialog's count-based copy - always shown, never
 * conditional, so a fresh/empty device shows up as "0 workouts" and makes the wrong-order mistake
 * (Claim before Pull, on a reformatted device) visible before it happens. Pull counts the cloud's
 * workouts, Claim this device's. [workoutCount] is null while still counting - the dialog opens
 * immediately and fills in. */
data class ReplaceConfirmation(val workoutCount: Int?)

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
    val runningOperation: RunningOperation? = null,
) {
    /** Every action button is disabled while anything at all is in flight. */
    val isIdle: Boolean get() = busyAction == null && runningOperation == null
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    observeAccountStatus: ObserveAccountStatusUseCase,
    private val signInUseCase: SignInUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val reauthenticateUseCase: ReauthenticateUseCase,
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
    private val runningOperation = MutableStateFlow<RunningOperation?>(null)
    private var countJob: Job? = null

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
        runningOperation,
    ) { values ->
        AccountUiState(
            account = values[0] as AuthAccount?,
            isSignInAvailable = signInUseCase.isAvailable,
            busyAction = values[1] as AccountAction?,
            errorReason = values[2] as AuthErrorReason?,
            syncStatus = values[3] as SyncStatus?,
            secondaryDeviceReason = values[4] as SecondaryDeviceReason?,
            replaceErrorReason = values[5] as SyncReplaceErrorReason?,
            pullConfirmation = values[6] as ReplaceConfirmation?,
            claimConfirmation = values[7] as ReplaceConfirmation?,
            nextScheduledSyncAt = values[8] as Long?,
            runningOperation = values[9] as RunningOperation?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    private fun isIdle() = busyAction.value == null && runningOperation.value == null

    fun signIn() {
        if (!isIdle()) return
        viewModelScope.launch {
            busyAction.value = AccountAction.SIGN_IN
            error.value = null
            replaceError.value = null
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
        if (!isIdle()) return
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

    /** Re-authenticates first - Firebase only deletes an account within a few minutes of a
     * sign-in, so asking up front means the last step normally succeeds rather than failing after
     * everything else is already gone. Nothing is deleted if this step fails or is dismissed. */
    fun deleteCloudData() {
        if (!isIdle()) return
        viewModelScope.launch {
            busyAction.value = AccountAction.DELETE_CLOUD_DATA
            error.value = null
            replaceError.value = null
            val reauthFailure = reauthenticateUseCase().exceptionOrNull()
            busyAction.value = null
            if (reauthFailure != null) {
                // Dismissing the sheet is the user's own choice, not an error.
                error.value = reauthFailure.toReason().takeIf { it != AuthErrorReason.CANCELLED }
                return@launch
            }
            runOperation(SyncOperation.DELETE_CLOUD_DATA) { onProgress ->
                deleteCloudDataUseCase(onProgress)
            }
        }
    }

    /** Out-of-band run of the same incremental push the periodic job does - not destructive, so
     * unlike Pull/Claim it needs no confirmation, just the same busy-spinner/
     * disabled-while-in-flight treatment every other action here uses. The button itself is
     * hidden for secondary devices (see AccountScreen.kt), so the [NOT_PRIMARY_STATUS] guard
     * below is defense-in-depth, not the primary mechanism. */
    fun syncNow() {
        if (!isIdle()) return
        viewModelScope.launch {
            busyAction.value = AccountAction.SYNC_NOW
            val result = syncNowUseCase()
            if (result != NOT_PRIMARY_STATUS) syncStatus.value = result
            busyAction.value = null
        }
    }

    /** Opens the confirmation straight away and fills in the cloud count when the server
     * answers - a failed count closes the dialog again with the error. */
    fun requestPullCloudData() {
        if (!isIdle()) return
        replaceError.value = null
        pullConfirmation.value = ReplaceConfirmation(workoutCount = null)
        countJob = viewModelScope.launch {
            try {
                val count = cloudWorkoutCountUseCase()
                pullConfirmation.update { it?.copy(workoutCount = count) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pullConfirmation.value = null
                replaceError.value = e.toReplaceReason()
            }
        }
    }

    fun dismissPullConfirmation() {
        countJob?.cancel()
        pullConfirmation.value = null
    }

    /** A pull resets the local freshness watermark to match the cloud - which can resolve
     * [SecondaryDeviceReason.STALE_LOCAL_STATE] for a device that was already primary but had
     * stale local state, so [refreshOnResume] re-checks afterward. */
    fun confirmPullCloudData() {
        pullConfirmation.value = null
        if (!isIdle()) return
        viewModelScope.launch {
            runOperation(SyncOperation.PULL_CLOUD_DATA) { onProgress ->
                pullCloudDataUseCase(onProgress)
            }
        }
    }

    /** See [requestPullCloudData] - same shape, but the count is local, so it lands almost
     * instantly. */
    fun requestClaimPrimary() {
        if (!isIdle()) return
        replaceError.value = null
        claimConfirmation.value = ReplaceConfirmation(workoutCount = null)
        countJob = viewModelScope.launch {
            val count = localWorkoutCountUseCase()
            claimConfirmation.update { it?.copy(workoutCount = count) }
        }
    }

    fun dismissClaimConfirmation() {
        countJob?.cancel()
        claimConfirmation.value = null
    }

    fun confirmClaimPrimary() {
        claimConfirmation.value = null
        if (!isIdle()) return
        viewModelScope.launch {
            val succeeded = runOperation(SyncOperation.CLAIM_PRIMARY) { onProgress ->
                claimPrimaryUseCase(onProgress)
            }
            if (succeeded) {
                secondaryDeviceReason.value = null
                // A device that was secondary already had its own periodic job self-cancel (it
                // noticed it wasn't primary and stopped itself) - nothing else re-schedules one
                // once it becomes primary again, so this is what actually restarts it. Idempotent
                // (KEEP), safe to call regardless of whether one somehow already exists.
                schedulePeriodicSyncUseCase()
            }
        }
    }

    /** Runs one of the three long operations behind the blocking progress dialog, feeding its
     * progress into [runningOperation]. Returns whether it succeeded; a failure lands in
     * [replaceError]. */
    private suspend fun runOperation(
        operation: SyncOperation,
        block: suspend (onProgress: suspend (SyncOperationProgress) -> Unit) -> Result<Unit>,
    ): Boolean {
        replaceError.value = null
        runningOperation.value =
            RunningOperation(operation, SyncOperationProgress(SyncOperationStep.STARTING))
        val result = try {
            block { progress -> runningOperation.value = RunningOperation(operation, progress) }
        } finally {
            runningOperation.value = null
        }
        result.onFailure { replaceError.value = it.toReplaceReason() }
        refreshOnResume()
        return result.isSuccess
    }

    private fun Throwable.toReason(): AuthErrorReason =
        (this as? AuthException)?.reason ?: AuthErrorReason.UNKNOWN

    /** Delete fails with [AuthException]s as well as [SyncReplaceException]s - both are shown in
     * the same place. */
    private fun Throwable.toReplaceReason(): SyncReplaceErrorReason = when (this) {
        is SyncReplaceException -> reason
        is AuthException -> when (reason) {
            AuthErrorReason.NETWORK -> SyncReplaceErrorReason.NETWORK
            AuthErrorReason.REAUTH_REQUIRED -> SyncReplaceErrorReason.REAUTH_REQUIRED
            AuthErrorReason.SESSION_REVOKED -> SyncReplaceErrorReason.SESSION_REVOKED
            else -> SyncReplaceErrorReason.UNKNOWN
        }

        else -> SyncReplaceErrorReason.UNKNOWN
    }
}
