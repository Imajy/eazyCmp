package com.aj.shared.plugin

import com.aj.shared.auth.OtpAutoReader
import com.aj.shared.notification.PushTokenManager
import com.aj.shared.platform.QrScanner

/**
 * Thin wrappers over platform expect stubs.
 * Prefer [com.aj.shared.plugin.adapters] CameraX / FCM / SMS implementations.
 */
class DefaultQrScannerService(
    private val delegate: QrScanner = QrScanner(),
) : QrScannerService {
    override fun startScan(onResult: QrScanCallback) = delegate.startScan(onResult)
    override fun stopScan() = delegate.stopScan()
    override val isAvailable: Boolean get() = delegate.isAvailable
    override val capability: ServiceCapability
        get() = if (delegate.isAvailable) ServiceCapability.FULL else ServiceCapability.HOST_REQUIRED
    override val hostHint: String?
        get() = if (delegate.isAvailable) null
        else "Use AndroidCameraXQrScanner / DesktopZxingQrScanner via EazyCmp.install."
}

class DefaultPushTokenService(
    private val delegate: PushTokenManager = PushTokenManager(),
) : PushTokenService {
    override suspend fun getToken(): String? = delegate.getToken()
    override fun onTokenRefresh(callback: (String) -> Unit) = delegate.onTokenRefresh(callback)
    override val capability: ServiceCapability = ServiceCapability.HOST_REQUIRED
    override val hostHint: String =
        "Prefer FcmPushTokenService / ApnsPushTokenService via EazyCmp.install."
}

class DefaultOtpAutoReadService(
    private val delegate: OtpAutoReader = OtpAutoReader(),
) : OtpAutoReadService {
    override fun startListening(listener: OtpAutoReadListener) = delegate.startListening(listener)
    override fun stopListening() = delegate.stopListening()
    override val isAvailable: Boolean get() = delegate.isAvailable
    override val capability: ServiceCapability
        get() = if (delegate.isAvailable) ServiceCapability.FULL else ServiceCapability.HOST_REQUIRED
    override val hostHint: String?
        get() = if (delegate.isAvailable) null
        else "Use SmsRetrieverOtpAutoRead via EazyCmp.install."
}
