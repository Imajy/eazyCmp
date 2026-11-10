package com.aj.shared.plugin.adapters

import com.aj.shared.plugin.PushTokenService
import com.aj.shared.plugin.ServiceCapability
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.registerForRemoteNotifications

/**
 * iOS APNs token bridge.
 *
 * From Swift/AppDelegate, convert `Data` to hex and call [onTokenReceivedHex].
 *
 * ```kotlin
 * EazyCmp.install { pushToken = ApnsPushTokenService.shared }
 * ApnsPushTokenService.shared.requestAuthorizationAndRegister()
 * // AppDelegate:
 * ApnsPushTokenService.shared.onTokenReceivedHex(tokenHex)
 * ```
 */
class ApnsPushTokenService private constructor() : PushTokenService {

    private var refreshCallback: ((String) -> Unit)? = null

    override val capability: ServiceCapability = ServiceCapability.PARTIAL
    override val hostHint: String =
        "Call ApnsPushTokenService.shared.onTokenReceivedHex(tokenHex) from AppDelegate."

    override suspend fun getToken(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(TOKEN_KEY)

    override fun onTokenRefresh(callback: (String) -> Unit) {
        refreshCallback = callback
    }

    fun requestAuthorizationAndRegister() {
        UIApplication.sharedApplication.registerForRemoteNotifications()
    }

    fun onTokenReceivedHex(tokenHex: String) {
        val normalized = tokenHex.lowercase().replace(" ", "")
        NSUserDefaults.standardUserDefaults.setObject(normalized, TOKEN_KEY)
        refreshCallback?.invoke(normalized)
    }

    companion object {
        val shared: ApnsPushTokenService by lazy { ApnsPushTokenService() }
        private const val TOKEN_KEY = "eazycmp.apns.token"
    }
}
