package com.aj.shared.platform

import com.aj.shared.plugin.QrScanCallback

/**
 * JVM stub — **HOST_REQUIRED**.
 *
 * QR capture is not provided on desktop JVM. Wire a host implementation via
 * `EazyCmp.install { qrScanner = ... }` (e.g. webcam + ZXing in the desktop app).
 */
actual class QrScanner actual constructor() {
    actual fun startScan(onResult: QrScanCallback) {
        onResult.onError("QR scanning is not available on JVM. Wire via EazyCmp.install { qrScanner = ... }.")
    }

    actual fun stopScan() = Unit

    actual val isAvailable: Boolean = false
}
