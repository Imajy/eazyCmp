package com.aj.shared.picker

import androidx.compose.runtime.Composable
import java.io.File
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

        when (type) {

            PickerType.IMAGE -> {

                chooser.currentDirectory =
                    File(System.getProperty("user.home"), "Pictures")

                chooser.fileFilter =
                    FileNameExtensionFilter(
                        "Images",
                        "jpg", "jpeg", "png", "webp"
                    )

            }

            PickerType.DOCUMENT -> {

                chooser.currentDirectory =
                    File(System.getProperty("user.home"), "Documents")

                chooser.fileFilter =
                    FileNameExtensionFilter(
                        "Documents",
                        "pdf", "doc", "docx"
                    )

            }

            PickerType.CAMERA -> {

                // fallback: open pictures directory
                chooser.currentDirectory =
                    File(System.getProperty("user.home"), "Pictures")

            }

            else -> {

                chooser.currentDirectory =
                    File(System.getProperty("user.home"))

            }

        }

        val result = chooser.showOpenDialog(null)

        if (result == JFileChooser.APPROVE_OPTION) {

            val file = chooser.selectedFile

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

private fun guessMimeType(ext: String): String {

    return when (ext.lowercase()) {

        "jpg", "jpeg" -> "image/jpeg"

        "png" -> "image/png"

        "webp" -> "image/webp"

        "pdf" -> "application/pdf"

        else -> "application/octet-stream"

    }

}