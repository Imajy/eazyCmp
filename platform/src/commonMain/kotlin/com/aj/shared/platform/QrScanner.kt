package com.aj.shared.platform

import com.aj.shared.plugin.HostRequired
import com.aj.shared.plugin.QrScanCallback

typealias PlatformQrScanCallback = QrScanCallback

/**
 * Platform QR scanner entry point.
 *
 * Prefer [com.aj.shared.plugin.QrScannerService] adapters (CameraX / ZXing) via EazyCmp.install.
 */
@HostRequired(
    reason = "Default QrScanner is a stub until CameraX, ZXing, or AVFoundation adapter is installed.",
)
expect class QrScanner() {
    fun startScan(onResult: QrScanCallback)
    fun stopScan()
    val isAvailable: Boolean
}
