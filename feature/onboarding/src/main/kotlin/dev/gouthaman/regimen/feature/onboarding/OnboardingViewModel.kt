package dev.gouthaman.regimen.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.AuthException
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import dev.gouthaman.regimen.domain.usecase.ObserveAccountStatusUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.SignInUseCase
import dev.gouthaman.regimen.domain.usecase.UpdatePreferencesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State for onboarding's optional sign-in page - deliberately narrower than `:feature:account`'s
 * `AccountUiState` (sign-in only, no sign-out/delete-cloud-data actions belong here). */
data class OnboardingSignInState(
    val account: AuthAccount? = null,
    val isSignInAvailable: Boolean = false,
    val isSigningIn: Boolean = false,
    val errorReason: AuthErrorReason? = null,
)

/**
 * First-run onboarding (S17). Selections are written to preferences immediately (so the app theme
 * updates live and a skip still keeps whatever was chosen); [finish] flips the onboarded flag,
 * which the app-level gate observes to leave onboarding.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    observePreferences: ObservePreferencesUseCase,
    private val updatePreferences: UpdatePreferencesUseCase,
    observeAccountStatus: ObserveAccountStatusUseCase,
    private val signInUseCase: SignInUseCase,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = observePreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val isSigningIn = MutableStateFlow(false)
    private val signInError = MutableStateFlow<AuthErrorReason?>(null)

    val signInState: StateFlow<OnboardingSignInState> = combine(
        observeAccountStatus(), isSigningIn, signInError,
    ) { account, busy, errorReason ->
        OnboardingSignInState(
            account = account,
            isSignInAvailable = signInUseCase.isAvailable,
            isSigningIn = busy,
            errorReason = errorReason,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingSignInState())

    fun setWeightUnit(value: UnitSystem) = viewModelScope.launch {
        updatePreferences.setWeightUnit(value)
    }

    fun setDistanceUnit(value: UnitSystem) = viewModelScope.launch {
        updatePreferences.setDistanceUnit(value)
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch {
        updatePreferences.setThemeMode(value)
    }

    fun setDynamicColor(value: Boolean) = viewModelScope.launch {
        updatePreferences.setDynamicColor(value)
    }

    fun signIn() {
        if (isSigningIn.value) return
        viewModelScope.launch {
            isSigningIn.value = true
            signInError.value = null
            signInUseCase().onFailure { signInError.value = it.toReason() }
            isSigningIn.value = false
        }
    }

    fun finish() = viewModelScope.launch {
        updatePreferences.setOnboarded(true)
    }

    private fun Throwable.toReason(): AuthErrorReason =
        (this as? AuthException)?.reason ?: AuthErrorReason.UNKNOWN
}
