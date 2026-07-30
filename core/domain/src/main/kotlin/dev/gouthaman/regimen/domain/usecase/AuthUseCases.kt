package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveAccountStatusUseCase @Inject constructor(
    private val repo: AuthRepository,
) {
    operator fun invoke(): StateFlow<AuthAccount?> = repo.account
}

class SignInUseCase @Inject constructor(
    private val repo: AuthRepository,
) {
    val isAvailable: Boolean get() = repo.isSignInAvailable

    suspend operator fun invoke(): Result<AuthAccount> = repo.signIn()
}

class SignOutUseCase @Inject constructor(
    private val repo: AuthRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repo.signOut()
}

class DeleteCloudDataUseCase @Inject constructor(
    private val repo: AuthRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repo.deleteCloudData()
}