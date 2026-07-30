package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.AuthAccount
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val account: StateFlow<AuthAccount?>
    val isSignInAvailable: Boolean

    suspend fun signIn(): Result<AuthAccount>
    suspend fun signOut(): Result<Unit>
    suspend fun deleteCloudData(): Result<Unit>
}
