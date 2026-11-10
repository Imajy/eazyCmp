package com.aj.shared.web

import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.FileReader
import org.w3c.files.get

actual class WebMediaPicker actual constructor() {
    actual fun pick(
        type: WebPickerType,
        accept: String?,
        onResult: (WebPickedFile?) -> Unit,
    ) {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = accept ?: when (type) {
            WebPickerType.IMAGE -> "image/*"
            WebPickerType.DOCUMENT -> ".pdf,.doc,.docx,.txt,application/pdf"
            WebPickerType.ANY -> "*/*"
        }
        input.onchange = { _: Event ->
            val file = input.files?.get(0)
            if (file == null) {
                onResult(null)
            } else {
                val reader = FileReader()
                reader.onload = {
                    val buffer = reader.result as? ArrayBuffer
                    if (buffer == null) {
                        onResult(null)
                    } else {
                        val bytes = arrayBufferToByteArray(buffer)
                        onResult(
                            WebPickedFile(
                                bytes = bytes,
                                fileName = file.name,
                                mimeType = file.type.ifBlank { null },
                            ),
                        )
                    }
                }
                reader.onerror = { onResult(null) }
                reader.readAsArrayBuffer(file)
            }
        }
        input.click()
    }
}

private fun arrayBufferToByteArray(buffer: ArrayBuffer): ByteArray {
    val view = Uint8Array(buffer)
    return ByteArray(view.length) { view[it] }
}
