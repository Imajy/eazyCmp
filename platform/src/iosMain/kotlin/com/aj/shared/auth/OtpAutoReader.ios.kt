package com.aj.shared.auth

import com.aj.shared.plugin.OtpAutoReadListener

/**
 * **HOST_REQUIRED** — OTP auto-read is not implemented on iOS; override
 * [com.aj.shared.plugin.EazyRegistry.otpAutoRead] in the host if needed.
 */
actual class OtpAutoReader actual constructor() {
    actual fun startListening(listener: OtpAutoReadListener) {
        listener.onError("OTP auto-read is not available on iOS.")
    }

    actual fun stopListening() = Unit

    actual val isAvailable: Boolean = false
}
