package com.aj.shared.notification

/**
 * JVM stub — **HOST_REQUIRED**.
 *
 * Desktop JVM has no FCM/APNs token. Wire push delivery via
 * `EazyCmp.install { pushToken = ... }` in the host application.
 */
actual class PushTokenManager actual constructor() {
    actual suspend fun getToken(): String? = null

    actual fun onTokenRefresh(callback: (String) -> Unit) = Unit
}
