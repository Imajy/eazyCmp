package com.aj.shared.picker

import androidx.compose.runtime.Composable
import org.bytedeco.javacpp.BytePointer
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.bytedeco.opencv.opencv_videoio.VideoCapture
import org.bytedeco.opencv.global.opencv_imgcodecs.imencode
import org.bytedeco.opencv.opencv_core.Mat

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
                captureFromCamera(onResult = onResult)
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

fun captureFromCamera(
    onResult: (PickedFile?) -> Unit
) {

    try {

        val camera = VideoCapture(0)

        if (!camera.isOpened) {

            println("camera not available")

            onResult(null)

            return

        }

        val frame = Mat()

        camera.read(frame)

        val buffer = BytePointer()

        imencode(".jpg", frame, buffer)

        val bytes = ByteArray(buffer.limit().toInt())

        buffer.get(bytes)

        camera.release()

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