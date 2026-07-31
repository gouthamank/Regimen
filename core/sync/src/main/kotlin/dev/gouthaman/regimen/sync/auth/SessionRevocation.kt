package dev.gouthaman.regimen.sync.auth

import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException

/** True for a stale/revoked Google session - local Firebase Auth state still looks signed-in, but
 * the first real network call discovers the grant is gone. Firestore surfaces this as a
 * permission/unauthenticated error rather than an auth-specific exception, since it's Firestore's
 * security rules (not Firebase Auth) that reject the stale token. */
internal fun Throwable.isSessionRevoked(): Boolean = when (this) {
    is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> true
    is FirebaseFirestoreException ->
        code == FirebaseFirestoreException.Code.UNAUTHENTICATED ||
                code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    else -> false
}
