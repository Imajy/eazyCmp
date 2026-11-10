package com.aj.shared.notification

import com.aj.shared.plugin.HostRequired

/**
 * FCM / APNs push token access.
 *
 * Default platform sources return no token; register [com.aj.shared.plugin.PushTokenService]
 * via [com.aj.shared.plugin.EazyPlugin.install].
 */
@HostRequired(
    reason = "Default PushTokenManager returns null until FCM or APNs is integrated in the host.",
)
expect class PushTokenManager() {
    suspend fun getToken(): String?
    fun onTokenRefresh(callback: (String) -> Unit)
}
