package dev.gouthaman.regimen.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.AuthException
import dev.gouthaman.regimen.domain.usecase.DeleteCloudDataUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveAccountStatusUseCase
import dev.gouthaman.regimen.domain.usecase.SignInUseCase
import dev.gouthaman.regimen.domain.usecase.SignOutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which action, if any, currently has a request in flight - lets the UI show a spinner on
 * specifically the button that triggered it while disabling every other button. */
enum class AccountAction { SIGN_IN, SIGN_OUT, DELETE_CLOUD_DATA }

data class AccountUiState(
    val account: AuthAccount? = null,
    val isSignInAvailable: Boolean = false,
    val busyAction: AccountAction? = null,
    val errorReason: AuthErrorReason? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    observeAccountStatus: ObserveAccountStatusUseCase,
    private val signInUseCase: SignInUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val deleteCloudDataUseCase: DeleteCloudDataUseCase,
) : ViewModel() {

    private val busyAction = MutableStateFlow<AccountAction?>(null)
    private val error = MutableStateFlow<AuthErrorReason?>(null)

    val uiState: StateFlow<AccountUiState> = combine(
        observeAccountStatus(), busyAction, error
    ) { account, busy, errorReason ->
        AccountUiState(
            account = account,
            isSignInAvailable = signInUseCase.isAvailable,
            busyAction = busy,
            errorReason = errorReason,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun signIn() {
        if (busyAction.value != null) return
        viewModelScope.launch {
            busyAction.value = AccountAction.SIGN_IN
            error.value = null
            signInUseCase().onFailure { error.value = it.toReason() }
            busyAction.value = null
        }
    }

    fun signOut() {
        if (busyAction.value != null) return
        viewModelScope.launch {
            busyAction.value = AccountAction.SIGN_OUT
            error.value = null
            signOutUseCase().onFailure { error.value = it.toReason() }
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

    private fun Throwable.toReason(): AuthErrorReason =
        (this as? AuthException)?.reason ?: AuthErrorReason.UNKNOWN
}