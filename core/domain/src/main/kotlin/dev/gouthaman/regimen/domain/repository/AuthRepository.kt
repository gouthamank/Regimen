package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.SyncOperationProgress
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val account: StateFlow<AuthAccount?>
    val isSignInAvailable: Boolean

    suspend fun signIn(): Result<AuthAccount>

    suspend fun signOut(): Result<Unit>

    /** Re-confirms the signed-in Google account, so a following [deleteCloudData] falls inside
     * Firebase's "recent sign-in" window. Fails with
     * [dev.gouthaman.regimen.domain.model.AuthErrorReason.WRONG_ACCOUNT] if a different account is
     * picked, [dev.gouthaman.regimen.domain.model.AuthErrorReason.CANCELLED] if the sheet is
     * dismissed. */
    suspend fun reauthenticate(): Result<Unit>

    /** Deletes every cloud document for the account, then the Firebase Auth account itself.
     * Expects [reauthenticate] to have just succeeded. Safe to run again after an interruption -
     * it finishes whatever is left. Fails with
     * [dev.gouthaman.regimen.domain.model.AuthException], or with
     * [dev.gouthaman.regimen.domain.model.SyncReplaceException]
     * ([dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason.PUSH_IN_PROGRESS]) while a push
     * or claim holds the `syncConfig` lock. */
    suspend fun deleteCloudData(
        onProgress: suspend (SyncOperationProgress) -> Unit = {},
    ): Result<Unit>
}
