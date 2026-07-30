package dev.gouthaman.regimen.feature.onboarding

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.AuthException
import dev.gouthaman.regimen.domain.usecase.ObserveAccountStatusUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.SignInUseCase
import dev.gouthaman.regimen.domain.usecase.UpdatePreferencesUseCase
import dev.gouthaman.regimen.testing.FakeAuthRepository
import dev.gouthaman.regimen.testing.FakePreferencesRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferencesRepo = FakePreferencesRepository()

    private fun viewModel(authRepo: FakeAuthRepository = FakeAuthRepository()) =
        OnboardingViewModel(
            observePreferences = ObservePreferencesUseCase(preferencesRepo),
            updatePreferences = UpdatePreferencesUseCase(preferencesRepo),
            observeAccountStatus = ObserveAccountStatusUseCase(authRepo),
            signInUseCase = SignInUseCase(authRepo),
        )

    @Test
    fun `starts signed out`() = runTest {
        val viewModel = viewModel()

        viewModel.signInState.test {
            assertNull(awaitItem().account)
        }
    }

    @Test
    fun `sign in updates state with the returned account`() = runTest {
        val account = AuthAccount(uid = "u1", email = "a@b.com", displayName = "A B")
        val viewModel = viewModel(FakeAuthRepository(signInResult = Result.success(account)))

        viewModel.signIn()

        viewModel.signInState.test {
            assertEquals(account, awaitItem().account)
        }
    }

    @Test
    fun `sign in failure with an AuthException surfaces its reason`() = runTest {
        val viewModel = viewModel(
            FakeAuthRepository(signInResult = Result.failure(AuthException(AuthErrorReason.NO_CREDENTIALS)))
        )

        viewModel.signIn()

        viewModel.signInState.test {
            assertEquals(AuthErrorReason.NO_CREDENTIALS, awaitItem().errorReason)
        }
    }

    @Test
    fun `sign in failure with an untyped exception falls back to UNKNOWN`() = runTest {
        val viewModel =
            viewModel(FakeAuthRepository(signInResult = Result.failure(RuntimeException("boom"))))

        viewModel.signIn()

        viewModel.signInState.test {
            assertEquals(AuthErrorReason.UNKNOWN, awaitItem().errorReason)
        }
    }

    @Test
    fun `finish marks onboarding complete`() = runTest {
        val viewModel = viewModel()

        viewModel.finish()

        assertTrue(preferencesRepo.preferences.first().onboarded)
    }
}
