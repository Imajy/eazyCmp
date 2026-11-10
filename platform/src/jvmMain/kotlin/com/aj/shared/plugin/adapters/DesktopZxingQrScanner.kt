package com.aj.shared.plugin.adapters

import com.aj.shared.plugin.QrScanCallback
import com.aj.shared.plugin.QrScannerService
import com.aj.shared.plugin.ServiceCapability
import com.github.sarxos.webcam.Webcam
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Desktop QR scanner using webcam-capture + ZXing.
 *
 * ```kotlin
 * EazyCmp.install { qrScanner = DesktopZxingQrScanner() }
 * ```
 */
class DesktopZxingQrScanner : QrScannerService {
    @Volatile private var running = false
    private var window: JFrame? = null
    private var webcam: Webcam? = null

    override val isAvailable: Boolean
        get() = try {
            Webcam.getDefault() != null
        } catch (_: Throwable) {
            false
        }

    override val capability: ServiceCapability =
        if (isAvailable) ServiceCapability.FULL else ServiceCapability.HOST_REQUIRED

    override val hostHint: String?
        get() = if (isAvailable) null else "No webcam available for DesktopZxingQrScanner."

    override fun startScan(onResult: QrScanCallback) {
        if (!isAvailable) {
            onResult.onError("No webcam found")
            return
        }
        if (running) return
        running = true

        Thread {
            try {
                val cam = Webcam.getDefault() ?: run {
                    running = false
                    onResult.onError("No webcam found")
                    return@Thread
                }
                webcam = cam
                cam.open()
                val preview = JLabel()
                val cancel = JButton("Cancel")
                val panel = JPanel(BorderLayout())
                panel.add(preview, BorderLayout.CENTER)
                panel.add(cancel, BorderLayout.SOUTH)
                val frame = JFrame("EazyCmp QR Scanner").apply {
                    contentPane = panel
                    setSize(640, 520)
                    setLocationRelativeTo(null)
                    isVisible = true
                }
                window = frame
                cancel.addActionListener {
                    stopScan()
                    onResult.onCancelled()
                }
                frame.addWindowListener(object : java.awt.event.WindowAdapter() {
                    override fun windowClosing(e: java.awt.event.WindowEvent?) {
                        stopScan()
                        onResult.onCancelled()
                    }
                })

                val reader = MultiFormatReader()
                while (running && cam.isOpen) {
                    val image: BufferedImage? = cam.image
                    if (image != null) {
                        SwingUtilities.invokeLater { preview.icon = ImageIcon(image) }
                        try {
                            val source = BufferedImageRgbLuminanceSource(image)
                            val bitmap = BinaryBitmap(HybridBinarizer(source))
                            val result = reader.decode(bitmap)
                            stopScan()
                            onResult.onResult(result.text)
                            return@Thread
                        } catch (_: NotFoundException) {
                            // keep scanning
                        } catch (_: Exception) {
                            // keep scanning
                        }
                    }
                    Thread.sleep(80)
                }
            } catch (t: Throwable) {
                stopScan()
                onResult.onError(t.message ?: "Desktop QR scan failed")
            }
        }.start()
    }

    override fun stopScan() {
        running = false
        try {
            webcam?.close()
        } catch (_: Throwable) {
        }
        webcam = null
        SwingUtilities.invokeLater {
            window?.dispose()
            window = null
        }
    }
}

private class BufferedImageRgbLuminanceSource(
    image: BufferedImage,
) : LuminanceSource(image.width, image.height) {
    private val luminances: ByteArray

    init {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)
        luminances = ByteArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            luminances[i] = ((r + g + g + b) shr 2).toByte()
        }
    }

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        require(y in 0 until height) { "y out of bounds" }
        val width = width
        val out = if (row == null || row.size < width) ByteArray(width) else row
        System.arraycopy(luminances, y * width, out, 0, width)
        return out
    }

    override fun getMatrix(): ByteArray = luminances
}
