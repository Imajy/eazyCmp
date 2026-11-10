package com.aj.shared.notification

/**
 * **HOST_REQUIRED** — returns no token until APNs is configured in the host app or
 * [com.aj.shared.plugin.EazyRegistry.pushToken] is overridden.
 */
actual class PushTokenManager actual constructor() {
    actual suspend fun getToken(): String? = null

    actual fun onTokenRefresh(callback: (String) -> Unit) = Unit
}
