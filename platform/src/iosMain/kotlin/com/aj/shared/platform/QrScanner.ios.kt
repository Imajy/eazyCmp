package com.aj.shared.platform

import com.aj.shared.plugin.QrScanCallback

/**
 * **HOST_REQUIRED** — stub only. Provide AVFoundation-based scanning in the host or override
 * [com.aj.shared.plugin.EazyRegistry.qrScanner].
 */
actual class QrScanner actual constructor() {
    actual fun startScan(onResult: QrScanCallback) {
        onResult.onError("QR scanning is not configured on iOS yet.")
    }

    actual fun stopScan() = Unit

    actual val isAvailable: Boolean = false
}
