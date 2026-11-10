package com.aj.shared.plugin.adapters

import android.app.Activity
import android.content.Context
import com.aj.shared.plugin.QrScanCallback
import com.aj.shared.plugin.QrScannerService
import com.aj.shared.plugin.ServiceCapability
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Built-in Android QR scanner using Google Play Code Scanner (CameraX under the hood via GMS).
 * No CAMERA permission required in the host manifest for the Play services scanner UI.
 *
 * ```kotlin
 * EazyCmp.install { qrScanner = AndroidCameraXQrScanner(activity) }
 * ```
 */
class AndroidCameraXQrScanner(
    private val context: Context,
) : QrScannerService {

    private var scanning = false

    override val isAvailable: Boolean = true
    override val capability: ServiceCapability = ServiceCapability.FULL
    override val hostHint: String? = null

    override fun startScan(onResult: QrScanCallback) {
        if (scanning) return
        scanning = true
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                scanning = false
                val value = barcode.rawValue
                if (value.isNullOrBlank()) onResult.onError("Empty QR payload")
                else onResult.onResult(value)
            }
            .addOnCanceledListener {
                scanning = false
                onResult.onCancelled()
            }
            .addOnFailureListener { error ->
                scanning = false
                onResult.onError(error.message ?: "QR scan failed")
            }
    }

    override fun stopScan() {
        scanning = false
    }
}

/** Alias matching docs that mention CameraX explicitly. */
typealias AndroidGmsQrScanner = AndroidCameraXQrScanner

fun Activity.eazyQrScanner(): QrScannerService = AndroidCameraXQrScanner(this)
