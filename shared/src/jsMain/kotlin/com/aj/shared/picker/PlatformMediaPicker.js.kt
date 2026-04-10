package com.aj.shared.picker

import androidx.compose.runtime.Composable
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

actual class PlatformMediaPicker actual constructor() {

    @Composable
    actual fun RegisterLaunchers() {
      // launcher not needed function is here just for consistency
    }

    actual fun launch(
        type: PickerType,
        documentConfig: DocumentConfig?,
        onResult: (PickedFile?) -> Unit
    ) {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"

        when (type) {
            PickerType.IMAGE -> {
                input.accept = "image/*"
            }
            PickerType.DOCUMENT -> {
                input.accept = documentConfig?.mimeTypes?.joinToString(",") ?: "*/*"
            }
            PickerType.CAMERA -> {
                input.accept = "image/*"
                input.setAttribute("capture", "environment")
            }
        }

        input.onchange = {
            val files = input.files
            if (files != null && files.length > 0) {
                val file = files[0]!!
                val reader = FileReader()

                reader.onload = { event ->
                    val arrayBuffer = reader.result as ArrayBuffer
                    val uint8Array = Uint8Array(arrayBuffer)

                    val bytes = ByteArray(uint8Array.length) { i -> uint8Array[i] }

                    onResult(
                        PickedFile(
                            bytes = bytes,
                            fileName = file.name,
                            mimeType = file.type
                        )
                    )
                }

                reader.onerror = {
                    onResult(null)
                }

                reader.readAsArrayBuffer(file)
            } else {
                onResult(null)
            }
        }

        input.click()
    }
}