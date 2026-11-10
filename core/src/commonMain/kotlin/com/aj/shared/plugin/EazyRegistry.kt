package com.aj.shared.plugin

import com.aj.shared.analytics.EazyAnalytics
import com.aj.shared.analytics.EazyCrash
import com.aj.shared.analytics.NoOpEazyAnalytics
import com.aj.shared.analytics.NoOpEazyCrash

class EazyRegistry constructor() {
    var analytics: EazyAnalytics = NoOpEazyAnalytics
    var crashReporter: EazyCrash = NoOpEazyCrash
    var qrScanner: QrScannerService = HostRequiredQrScannerService
    var pushToken: PushTokenService = HostRequiredPushTokenService
    var otpAutoRead: OtpAutoReadService = HostRequiredOtpAutoReadService
    var secureStorage: SecureStorageService? = null // null = platform SecureStorage via EazyCmp.storage

    private val extras = mutableMapOf<String, Any>()

    fun register(key: String, service: Any) {
        extras[key] = service
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = extras[key] as? T

    fun keys(): Set<String> = extras.keys.toSet()
}
