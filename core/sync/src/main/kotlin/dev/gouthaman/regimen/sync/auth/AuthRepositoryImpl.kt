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
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.AuthException
import dev.gouthaman.regimen.domain.repository.AuthRepository
import dev.gouthaman.regimen.sync.di.WebClientId
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
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(context, request)
            val googleIdTokenCredential =
                GoogleIdTokenCredential.createFrom(response.credential.data)
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

    private val syncedSubcollections = listOf(
        "workouts", "routines", "exercises", "measurementTypes", "bodyMetrics", "syncConfig",
    )

    override suspend fun deleteCloudData(): Result<Unit> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(
                AuthException(
                    AuthErrorReason.UNKNOWN,
                    IllegalStateException("No signed-in user")
                )
            )
        return try {
            val userDoc = firestore.collection("users").document(uid)
            for (name in syncedSubcollections) {
                val docs = userDoc.collection(name).get().await()
                for (doc in docs.documents) doc.reference.delete().await()
            }
            userDoc.collection("preferences").document("current").delete().await()
            userDoc.delete().await()
            firebaseAuth.currentUser?.delete()?.await()

            Result.success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(AuthException(AuthErrorReason.REAUTH_REQUIRED, e))
        } catch (e: FirebaseNetworkException) {
            Result.failure(AuthException(AuthErrorReason.NETWORK, e))
        } catch (e: Exception) {
            Result.failure(AuthException(AuthErrorReason.UNKNOWN, e))
        }
    }

    private fun FirebaseUser.toAuthAccount() = AuthAccount(
        uid = uid,
        email = email,
        displayName = displayName,
    )
}
