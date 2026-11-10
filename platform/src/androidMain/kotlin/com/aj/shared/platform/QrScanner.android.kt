package com.aj.shared.platform

import com.aj.shared.plugin.QrScanCallback

/**
 * Android stub — **HOST_REQUIRED**.
 *
 * Default source does not start CameraX / ML Kit. Wire scanning in the host via
 * `EazyCmp.install { qrScanner = ... }`.
 */
actual class QrScanner actual constructor() {
    actual fun startScan(onResult: QrScanCallback) {
        onResult.onError(
            "QR scanning is not configured. Wire via EazyCmp.install { qrScanner = ... } (CameraX / ML Kit).",
        )
    }

    actual fun stopScan() = Unit

    actual val isAvailable: Boolean = false
}
