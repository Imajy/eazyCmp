@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.aj.shared.web

private fun jsPickFile(accept: String, callback: (JsAny?) -> Unit): Unit = js(
    """{
    var input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.onchange = function() {
        var file = input.files && input.files[0];
        if (!file) { callback(null); return; }
        var reader = new FileReader();
        reader.onload = function() {
            callback({
              name: file.name,
              type: file.type || '',
              dataUrl: reader.result
            });
        };
        reader.onerror = function() { callback(null); };
        reader.readAsDataURL(file);
    };
    input.click();
}""",
)

private fun jsGetName(obj: JsAny): String = js("obj.name")
private fun jsGetType(obj: JsAny): String = js("obj.type")
private fun jsGetDataUrl(obj: JsAny): String = js("obj.dataUrl")

actual class WebMediaPicker actual constructor() {
    actual fun pick(
        type: WebPickerType,
        accept: String?,
        onResult: (WebPickedFile?) -> Unit,
    ) {
        val resolvedAccept = accept ?: when (type) {
            WebPickerType.IMAGE -> "image/*"
            WebPickerType.DOCUMENT -> ".pdf,.doc,.docx,.txt,application/pdf"
            WebPickerType.ANY -> "*/*"
        }
        jsPickFile(resolvedAccept) { raw ->
            if (raw == null) {
                onResult(null)
                return@jsPickFile
            }
            try {
                val name = jsGetName(raw)
                val mime = jsGetType(raw)
                val dataUrl = jsGetDataUrl(raw)
                val base64 = dataUrl.substringAfter("base64,", missingDelimiterValue = "")
                val bytes = if (base64.isBlank()) ByteArray(0) else decodeBase64(base64)
                onResult(
                    WebPickedFile(
                        bytes = bytes,
                        fileName = name,
                        mimeType = mime.ifBlank { null },
                    ),
                )
            } catch (_: Throwable) {
                onResult(null)
            }
        }
    }
}

private fun decodeBase64(value: String): ByteArray {
    val table = IntArray(256) { -1 }
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    for (i in chars.indices) table[chars[i].code] = i
    val cleaned = value.trim().trimEnd('=')
    val out = ArrayList<Byte>()
    var buffer = 0
    var bits = 0
    for (ch in cleaned) {
        val v = table.getOrElse(ch.code) { -1 }
        if (v < 0) continue
        buffer = (buffer shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.add(((buffer shr bits) and 0xff).toByte())
        }
    }
    return out.toByteArray()
}
