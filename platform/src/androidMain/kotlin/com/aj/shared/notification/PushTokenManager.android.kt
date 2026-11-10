package com.aj.shared.notification

/**
 * Android stub — **HOST_REQUIRED**.
 *
 * Default source does not register with FCM. Wire token access via
 * `EazyCmp.install { pushToken = ... }` (Firebase Messaging in the host app).
 */
actual class PushTokenManager actual constructor() {
    actual suspend fun getToken(): String? = null

    actual fun onTokenRefresh(callback: (String) -> Unit) = Unit
}
