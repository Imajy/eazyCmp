package com.aj.shared.picker

import androidx.compose.runtime.Composable
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.bytedeco.javacv.OpenCVFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.*
import java.io.ByteArrayOutputStream

actual class PlatformMediaPicker actual constructor() {

    @Composable
    actual fun RegisterLaunchers() {
    }

    actual fun launch(
        type: PickerType,
        documentConfig: DocumentConfig?,
        onResult: (PickedFile?) -> Unit
    ) {

        val chooser = JFileChooser()

        chooser.isMultiSelectionEnabled = false

        // remove "All files" option
        chooser.isAcceptAllFileFilterUsed = false

        when (type) {

            PickerType.IMAGE -> {

                chooser.currentDirectory =
                    File(System.getProperty("user.home"), "Pictures")

                chooser.fileFilter =
                    FileNameExtensionFilter(
                        "Images only",
                        "jpg",
                        "jpeg",
                        "png",
                        "webp"
                    )
            }

            PickerType.DOCUMENT -> {

                chooser.currentDirectory =
                    File(System.getProperty("user.home"), "Documents")

                chooser.fileFilter =
                    FileNameExtensionFilter(
                        "Documents only",
                        "pdf",
                        "doc",
                        "docx"
                    )
            }

            PickerType.CAMERA -> {
                safeCameraCapture(onResult = onResult)
            }
        }
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            if (!file.exists() || file.isDirectory) {
                onResult(null)
                return
            }
            // final validation safety check
            if (!isValidSelection(type, file)) {
                println("invalid file type selected")
                onResult(null)
                return
            }
            val bytes = file.readBytes()
            onResult(
                PickedFile(
                    bytes = bytes,
                    fileName = file.name,
                    mimeType = guessMimeType(file.extension)
                )
            )
        } else {
            onResult(null)
        }
    }
}

private fun isValidSelection(
    type: PickerType,
    file: File
): Boolean {

    val ext = file.extension.lowercase()

    return when (type) {

        PickerType.IMAGE,
        PickerType.CAMERA -> {

            ext in listOf(
                "jpg",
                "jpeg",
                "png",
                "webp"
            )
        }

        PickerType.DOCUMENT -> {

            ext in listOf(
                "pdf",
                "doc",
                "docx"
            )
        }
    }
}

private fun guessMimeType(ext: String): String {
    return when (ext.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }
}

fun safeFileChooser(

    folder: String?,

    extensions: List<String>?,

    onResult: (PickedFile?) -> Unit

) {

    try {

        val chooser = JFileChooser()

        chooser.isMultiSelectionEnabled = false

        extensions?.let {

            chooser.isAcceptAllFileFilterUsed = false

            chooser.fileFilter =
                FileNameExtensionFilter(

                    "Allowed files",

                    *it.toTypedArray()

                )

        }

        folder?.let {

            val dir = File(

                System.getProperty("user.home"),
                it
            )

            if (dir.exists()) {

                chooser.currentDirectory = dir

            }

        }

        val result = chooser.showOpenDialog(null)

        if (result == JFileChooser.APPROVE_OPTION) {

            val file = chooser.selectedFile

            if (!file.exists() || !file.isFile) {

                onResult(null)

                return

            }

            val bytes = file.readBytes()

            onResult(

                PickedFile(

                    bytes = bytes,

                    fileName = file.name,

                    mimeType = guessMime(file.extension)

                )

            )

        } else {

            onResult(null)

        }

    } catch (e: Exception) {

        e.printStackTrace()

        onResult(null)

    }

}

fun guessMime(ext: String): String {

    return when (ext.lowercase()) {

        "jpg", "jpeg" -> "image/jpeg"

        "png" -> "image/png"

        "webp" -> "image/webp"

        "pdf" -> "application/pdf"

        "doc" -> "application/msword"

        "docx" ->
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        else -> "application/octet-stream"

    }

}

fun safeCameraCapture(
    onResult: (PickedFile?) -> Unit
) {

    try {

        val grabber = OpenCVFrameGrabber(0)

        grabber.start()

        val converter = Java2DFrameConverter()

        val frame = grabber.grab()

        val previewImage: BufferedImage =
            converter.convert(frame)

        val label = JLabel(ImageIcon(previewImage))

        val captureBtn = JButton("Capture")

        val frameWindow = JFrame("Camera")

        frameWindow.layout = BoxLayout(
            frameWindow.contentPane,
            BoxLayout.Y_AXIS
        )
        frameWindow.add(label)
        frameWindow.add(captureBtn)
        frameWindow.setSize(400, 400)
        frameWindow.isVisible = true
        captureBtn.addActionListener {
            try {
                val capturedFrame = grabber.grab()
                val img = converter.convert(capturedFrame)
                val baos = ByteArrayOutputStream()
                ImageIO.write(img, "jpg", baos)
                val bytes = baos.toByteArray()
                frameWindow.dispose()
                grabber.stop()
                onResult(
                    PickedFile(
                        bytes = bytes,
                        fileName = "capture_${System.currentTimeMillis()}.jpg",
                        mimeType = "image/jpeg"
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                frameWindow.dispose()
                onResult(null)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        println("camera failed → fallback")
        safeFileChooser(
            folder = "Pictures",
            extensions = listOf(
                "jpg",
                "jpeg",
                "png"
            ),
            onResult
        )
    }
}