package dev.gouthaman.regimen.sync.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Per Firestore call - a single read, batch commit, or transaction. Offline, a write's `await()`
 * never completes on its own (it waits for a server ack indefinitely), so every call needs one. */
internal const val NETWORK_CALL_TIMEOUT_MS = 60_000L

/** Thrown when this call's own timeout expires - an ordinary exception, unlike the
 * `TimeoutCancellationException` underneath, so callers handle it as a network failure rather than
 * as their own coroutine being cancelled. */
internal class SyncTimeoutException : Exception()

/** Only this call's own timeout becomes [SyncTimeoutException]. An enclosing `withTimeout` (the
 * push job's whole-run backstop) or a cancellation still propagates as cancellation - catching
 * `TimeoutCancellationException` here instead would swallow those too. */
internal suspend fun <T> Task<T>.awaitOrTimeout(timeoutMs: Long = NETWORK_CALL_TIMEOUT_MS): T {
    var completed = false
    val result = withTimeoutOrNull(timeoutMs) {
        await().also { completed = true }
    }
    if (!completed) throw SyncTimeoutException()
    @Suppress("UNCHECKED_CAST")
    return result as T
}

/** A server read with `Source.SERVER` that loses its connection fails with `UNAVAILABLE`, not
 * [FirebaseNetworkException], so both count. */
internal fun Throwable.isNetworkFailure(): Boolean = when (this) {
    is FirebaseNetworkException, is SyncTimeoutException -> true
    is FirebaseFirestoreException ->
        code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED

    else -> false
}

internal fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
