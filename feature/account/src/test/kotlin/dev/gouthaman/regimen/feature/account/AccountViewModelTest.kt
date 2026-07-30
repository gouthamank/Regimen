package dev.gouthaman.regimen.feature.account

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.AuthException
import dev.gouthaman.regimen.domain.usecase.DeleteCloudDataUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveAccountStatusUseCase
import dev.gouthaman.regimen.domain.usecase.SignInUseCase
import dev.gouthaman.regimen.domain.usecase.SignOutUseCase
import dev.gouthaman.regimen.testing.FakeAuthRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(repo: FakeAuthRepository) = AccountViewModel(
        observeAccountStatus = ObserveAccountStatusUseCase(repo),
        signInUseCase = SignInUseCase(repo),
        signOutUseCase = SignOutUseCase(repo),
        deleteCloudDataUseCase = DeleteCloudDataUseCase(repo),
    )

    @Test
    fun `starts signed out`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())
        viewModel.uiState.test {
            assertNull(awaitItem().account)
        }
    }

    @Test
    fun `sign in updates state with the returned account`() = runTest {
        val account = AuthAccount(uid = "u1", email = "a@b.com", displayName = "A B")
        val repo = FakeAuthRepository(signInResult = Result.success(account))
        val viewModel = viewModel(repo)

        viewModel.signIn()

        viewModel.uiState.test {
            assertEquals(account, awaitItem().account)
        }
    }

    @Test
    fun `sign in failure with an AuthException surfaces its reason`() = runTest {
        val repo = FakeAuthRepository(
            signInResult = Result.failure(AuthException(AuthErrorReason.NO_CREDENTIALS))
        )
        val viewModel = viewModel(repo)

        viewModel.signIn()

        viewModel.uiState.test {
            assertEquals(AuthErrorReason.NO_CREDENTIALS, awaitItem().errorReason)
        }
    }

    @Test
    fun `sign in failure with an untyped exception falls back to UNKNOWN`() = runTest {
        val repo = FakeAuthRepository(signInResult = Result.failure(RuntimeException("boom")))
        val viewModel = viewModel(repo)

        viewModel.signIn()

        viewModel.uiState.test {
            assertEquals(AuthErrorReason.UNKNOWN, awaitItem().errorReason)
        }
    }

    @Test
    fun `sign out clears the account`() = runTest {
        val account = AuthAccount(uid = "u1", email = "a@b.com", displayName = "A B")
        val repo = FakeAuthRepository().apply { seedSignedIn(account) }
        val viewModel = viewModel(repo)

        viewModel.signOut()

        viewModel.uiState.test {
            assertNull(awaitItem().account)
        }
        assert(repo.signOutCalled)
    }

    @Test
    fun `delete cloud data invokes the repo`() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = viewModel(repo)

        viewModel.deleteCloudData()

        assert(repo.deleteCloudDataCalled)
    }

    @Test
    fun `delete cloud data failure with a stale session surfaces REAUTH_REQUIRED`() = runTest {
        val repo = FakeAuthRepository(
            deleteCloudDataResult = Result.failure(AuthException(AuthErrorReason.REAUTH_REQUIRED))
        )
        val viewModel = viewModel(repo)

        viewModel.deleteCloudData()

        viewModel.uiState.test {
            assertEquals(AuthErrorReason.REAUTH_REQUIRED, awaitItem().errorReason)
        }
    }
}