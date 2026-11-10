package com.aj.shared.web

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual class WebMediaPicker actual constructor() {
    actual fun pick(
        type: WebPickerType,
        accept: String?,
        onResult: (WebPickedFile?) -> Unit,
    ) {
        try {
            val chooser = JFileChooser()
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            when (type) {
                WebPickerType.IMAGE ->
                    chooser.fileFilter = FileNameExtensionFilter("Images", "jpg", "jpeg", "png", "webp", "gif")
                WebPickerType.DOCUMENT ->
                    chooser.fileFilter = FileNameExtensionFilter("Documents", "pdf", "doc", "docx", "txt")
                WebPickerType.ANY -> Unit
            }
            val result = chooser.showOpenDialog(null)
            if (result != JFileChooser.APPROVE_OPTION) {
                onResult(null)
                return
            }
            val file: File = chooser.selectedFile ?: run {
                onResult(null)
                return
            }
            onResult(
                WebPickedFile(
                    bytes = file.readBytes(),
                    fileName = file.name,
                    mimeType = guessMime(file.extension),
                ),
            )
        } catch (_: Throwable) {
            onResult(null)
        }
    }
}

private fun guessMime(ext: String): String = when (ext.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "pdf" -> "application/pdf"
    else -> "application/octet-stream"
}
