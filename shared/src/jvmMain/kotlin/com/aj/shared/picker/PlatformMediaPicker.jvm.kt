package com.aj.shared.picker

import androidx.compose.runtime.Composable
import javax.swing.JFileChooser

actual class PlatformMediaPicker actual constructor() {
    @Composable
    actual fun RegisterLaunchers() {}

    actual fun launch(
        type: PickerType,
        documentConfig: DocumentConfig?,
        onResult: (PickedFile?) -> Unit
    ) {
        val chooser = JFileChooser()

        val result = chooser.showOpenDialog(null)

        if (result == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile

            val bytes = file.readBytes()

            onResult(
                PickedFile(
                    bytes = bytes,
                    fileName = file.name,
                    mimeType = null
                )
            )
        } else { onResult(null) }
    }
}