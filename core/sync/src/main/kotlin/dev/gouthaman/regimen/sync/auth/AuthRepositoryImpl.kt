package dev.gouthaman.regimen.sync.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.AuthException
import dev.gouthaman.regimen.domain.model.SyncOperationProgress
import dev.gouthaman.regimen.domain.model.SyncOperationStep
import dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason
import dev.gouthaman.regimen.domain.model.SyncReplaceException
import dev.gouthaman.regimen.domain.repository.AuthRepository
import dev.gouthaman.regimen.sync.device.LOCK_STALE_AFTER_MS
import dev.gouthaman.regimen.sync.device.SyncConfigDto
import dev.gouthaman.regimen.sync.di.WebClientId
import dev.gouthaman.regimen.sync.firestore.FirestoreSyncReader
import dev.gouthaman.regimen.sync.network.awaitOrTimeout
import dev.gouthaman.regimen.sync.network.isNetworkAvailable
import dev.gouthaman.regimen.sync.network.isNetworkFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    @WebClientId private val webClientId: String,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    private val _account = MutableStateFlow(firebaseAuth.currentUser?.toAuthAccount())
    override val account: StateFlow<AuthAccount?> = _account

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _account.update { auth.currentUser?.toAuthAccount() }
        }
    }

    override val isSignInAvailable: Boolean
        get() = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    override suspend fun signIn(): Result<AuthAccount> {
        return try {
            val googleIdTokenCredential = requestGoogleCredential()
            val firebaseCredential =
                GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
            val account = authResult.user?.toAuthAccount()
                ?: return Result.failure(
                    AuthException(
                        AuthErrorReason.UNKNOWN,
                        IllegalStateException("Sign-in succeeded with no FirebaseUser")
                    )
                )
            Result.success(account)
        } catch (e: NoCredentialException) {
            Result.failure(AuthException(AuthErrorReason.NO_CREDENTIALS, e))
        } catch (e: GetCredentialCancellationException) {
            Result.failure(AuthException(AuthErrorReason.CANCELLED, e))
        } catch (e: GetCredentialException) {
            Result.failure(AuthException(AuthErrorReason.UNKNOWN, e))
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(AuthException(AuthErrorReason.UNKNOWN, e))
        } catch (e: FirebaseNetworkException) {
            Result.failure(AuthException(AuthErrorReason.NETWORK, e))
        }
    }

    override suspend fun signOut(): Result<Unit> {
        firebaseAuth.signOut()
        credentialManager.clearCredentialState(ClearCredentialStateRequest())

        return Result.success(Unit)
    }

    /** Offers every Google account on the device, not just ones that have used Regimen - the
     * signed-in account may have been removed from the device while its Firebase session lived
     * on, and a filtered list could then hold only other accounts. Picking the wrong one is
     * rejected below. Auto-select stays off, so the sheet always asks. */
    override suspend fun reauthenticate(): Result<Unit> {
        val user = firebaseAuth.currentUser
            ?: return Result.failure(
                AuthException(AuthErrorReason.UNKNOWN, IllegalStateException("No signed-in user"))
            )
        // Checked up front - otherwise the account sheet can still open offline and the failure
        // only shows after the user has picked an account.
        if (!context.isNetworkAvailable()) {
            return Result.failure(AuthException(AuthErrorReason.NETWORK))
        }
        return try {
            val googleIdTokenCredential = requestGoogleCredential()
            // Caught before Firebase sees it, which also rejects a mismatch - but only after a
            // network round trip, and with a less specific error.
            if (user.email != null && !googleIdTokenCredential.id.equals(
                    user.email,
                    ignoreCase = true
                )
            ) {
                return Result.failure(AuthException(AuthErrorReason.WRONG_ACCOUNT))
            }
            val firebaseCredential =
                GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            user.reauthenticate(firebaseCredential).awaitOrTimeout()
            Result.success(Unit)
        } catch (e: NoCredentialException) {
            Result.failure(AuthException(AuthErrorReason.NO_CREDENTIALS, e))
        } catch (e: GetCredentialCancellationException) {
            Result.failure(AuthException(AuthErrorReason.CANCELLED, e))
        } catch (e: GetCredentialException) {
            Result.failure(AuthException(AuthErrorReason.UNKNOWN, e))
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(AuthException(AuthErrorReason.UNKNOWN, e))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            // Firebase reports both a different account and a malformed/expired credential this
            // way, told apart only by error code. Neither means the session itself is gone, so
            // this must not fall through to [toAuthException], which would sign the user out.
            val reason = if (e.errorCode == USER_MISMATCH_ERROR_CODE) {
                AuthErrorReason.WRONG_ACCOUNT
            } else {
                AuthErrorReason.UNKNOWN
            }
            Result.failure(AuthException(reason, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toAuthException())
        }
    }

    /** Deletes `syncConfig` first, in one transaction with the lock check: without it no device is
     * primary, so this device's periodic push stops for good - even if this run is interrupted,
     * nothing can upload into a half-deleted tree. It refuses while a push or claim holds a fresh
     * lock rather than deleting out from under it. Then lists and deletes the data, then the user
     * document, and deletes the Auth account last - Firestore's security rules need it to still
     * exist for the deletes above it, and it's the only step that needs a recent sign-in. Running
     * it again after an interruption simply finishes what's left. */
    override suspend fun deleteCloudData(
        onProgress: suspend (SyncOperationProgress) -> Unit,
    ): Result<Unit> {
        val user = firebaseAuth.currentUser
            ?: return Result.failure(
                AuthException(AuthErrorReason.UNKNOWN, IllegalStateException("No signed-in user"))
            )
        if (!context.isNetworkAvailable()) {
            return Result.failure(AuthException(AuthErrorReason.NETWORK))
        }
        val uid = user.uid
        val userDoc = firestore.collection("users").document(uid)
        val syncConfigRef = userDoc.collection("syncConfig").document("current")

        return try {
            onProgress(SyncOperationProgress(SyncOperationStep.STARTING))
            firestore.runTransaction { transaction ->
                val lockedAt =
                    transaction.get(syncConfigRef).toObject(SyncConfigDto::class.java)?.lockedAt
                if (lockedAt != null && System.currentTimeMillis() - lockedAt < LOCK_STALE_AFTER_MS) {
                    throw SyncReplaceException(SyncReplaceErrorReason.PUSH_IN_PROGRESS)
                }
                transaction.delete(syncConfigRef)
            }.awaitOrTimeout()

            val reader = FirestoreSyncReader(firestore, uid)
            onProgress(SyncOperationProgress(SyncOperationStep.FINDING_CLOUD_DATA))
            val tree = reader.listAll { listed ->
                onProgress(SyncOperationProgress(SyncOperationStep.FINDING_CLOUD_DATA, listed))
            }
            reader.deleteAll(tree) { deleted, total ->
                onProgress(
                    SyncOperationProgress(
                        SyncOperationStep.DELETING_CLOUD_DATA,
                        deleted,
                        total
                    )
                )
            }
            userDoc.delete().awaitOrTimeout()

            onProgress(SyncOperationProgress(SyncOperationStep.DELETING_ACCOUNT))
            user.delete().awaitOrTimeout()

            Result.success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(AuthException(AuthErrorReason.REAUTH_REQUIRED, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A refusal thrown inside the transaction can come back wrapped.
            val refusal = e as? SyncReplaceException ?: e.cause as? SyncReplaceException
            Result.failure(refusal ?: e.toAuthException())
        }
    }

    private suspend fun requestGoogleCredential(): GoogleIdTokenCredential {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val response = credentialManager.getCredential(context, request)
        return GoogleIdTokenCredential.createFrom(response.credential.data)
    }

    /** A revoked grant can surface as any number of underlying exception shapes depending on which
     * call hit it first, so it takes priority over the network/unknown fallbacks. */
    private fun Throwable.toAuthException(): AuthException = when {
        isSessionRevoked() -> {
            firebaseAuth.signOut()
            AuthException(AuthErrorReason.SESSION_REVOKED, this)
        }

        isNetworkFailure() -> AuthException(AuthErrorReason.NETWORK, this)
        else -> AuthException(AuthErrorReason.UNKNOWN, this)
    }

    private fun FirebaseUser.toAuthAccount() = AuthAccount(
        uid = uid,
        email = email,
        displayName = displayName,
    )
}

/** Firebase Auth's code for re-authenticating with a credential that belongs to a different user
 * than the one signed in. */
private const val USER_MISMATCH_ERROR_CODE = "ERROR_USER_MISMATCH"
