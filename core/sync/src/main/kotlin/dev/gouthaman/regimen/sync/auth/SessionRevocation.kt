package dev.gouthaman.regimen.sync.auth

import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException

/** True for a stale/revoked Google session - the local Firebase Auth state still looks signed-in,
 * but the actual grant no longer exists (e.g. revoked from Google Account settings), so the first
 * real network call is what discovers it. Firestore surfaces this as a permission/unauthenticated
 * error rather than an auth-specific exception type, since it's Firestore's security rules (not
 * Firebase Auth itself) that reject the stale token. Shared by [dev.gouthaman.regimen.sync.push.SyncPushRunner]
 * and [AuthRepositoryImpl] - both are places a signed-in-looking session can hit a revoked grant. */
internal fun Throwable.isSessionRevoked(): Boolean = when (this) {
    is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> true
    is FirebaseFirestoreException ->
        code == FirebaseFirestoreException.Code.UNAUTHENTICATED ||
                code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    else -> false
}
