package com.aj.shared.auth

import com.aj.shared.plugin.OtpAutoReadListener

/**
 * **HOST_REQUIRED** — OTP auto-read is not available on JVM; override
 * [com.aj.shared.plugin.EazyRegistry.otpAutoRead] if the host provides OTP delivery.
 */
actual class OtpAutoReader actual constructor() {
    actual fun startListening(listener: OtpAutoReadListener) {
        listener.onError("OTP auto-read is not available on JVM.")
    }

    actual fun stopListening() = Unit

    actual val isAvailable: Boolean = false
}
