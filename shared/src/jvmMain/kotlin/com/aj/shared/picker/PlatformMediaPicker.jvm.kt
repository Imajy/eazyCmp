package com.aj.shared.picker

import androidx.compose.runtime.Composable
import com.github.sarxos.webcam.Webcam
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual class PlatformMediaPicker actual constructor() {

    @Composable
    actual fun RegisterLaunchers() {}

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

            PickerType.CAMERA -> {captureFromCamera(onResult = onResult)}
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

fun captureFromCamera(
    onResult: (PickedFile?) -> Unit
) {
    try {
        val webcam = Webcam.getDefault()
        if (webcam == null) {
            println("no camera detected")
            onResult(null)
            return
        }
        webcam.open()
        val image = webcam.image
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", output)
        val bytes = output.toByteArray()
        webcam.close()
        onResult(
            PickedFile(
                bytes = bytes,
                fileName = "camera_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg"
            )
        )
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(null)
    }
}