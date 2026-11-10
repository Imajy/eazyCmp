package com.aj.shared.auth

import com.aj.shared.plugin.HostRequired
import com.aj.shared.plugin.OtpAutoReadListener

/**
 * SMS / OTP auto-read hook.
 *
 * Prefer [com.aj.shared.plugin.OtpAutoReadService] (SmsRetrieverOtpAutoRead) via EazyCmp.install.
 */
@HostRequired(
    reason = "Default OtpAutoReader is unavailable until SMS Retriever adapter is installed.",
)
expect class OtpAutoReader() {
    fun startListening(listener: OtpAutoReadListener)
    fun stopListening()
    val isAvailable: Boolean
}
