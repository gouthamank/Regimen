package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthRepository(
    override val isSignInAvailable: Boolean = true,
    private val signInResult: Result<AuthAccount> = Result.success(
        AuthAccount(uid = "uid-1", email = "test@example.com", displayName = "Test User")
    ),
    private val signOutResult: Result<Unit> = Result.success(Unit),
    private val deleteCloudDataResult: Result<Unit> = Result.success(Unit),
) : AuthRepository {

    private val _account = MutableStateFlow<AuthAccount?>(null)
    override val account: StateFlow<AuthAccount?> = _account

    var signOutCalled = false
        private set
    var deleteCloudDataCalled = false
        private set

    override suspend fun signIn(): Result<AuthAccount> {
        signInResult.onSuccess { _account.value = it }
        return signInResult
    }

    override suspend fun signOut(): Result<Unit> {
        signOutCalled = true
        _account.value = null
        return signOutResult
    }

    override suspend fun deleteCloudData(): Result<Unit> {
        deleteCloudDataCalled = true
        return deleteCloudDataResult
    }

    fun seedSignedIn(account: AuthAccount) {
        _account.value = account
    }
}