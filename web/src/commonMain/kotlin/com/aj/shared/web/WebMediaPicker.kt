package com.aj.shared.web

enum class WebPickerType {
    IMAGE,
    DOCUMENT,
    ANY,
}

data class WebPickedFile(
    val bytes: ByteArray,
    val fileName: String?,
    val mimeType: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebPickedFile) return false
        return bytes.contentEquals(other.bytes) && fileName == other.fileName && mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        return result
    }
}

/**
 * Browser file picker (HTML input[type=file]).
 */
expect class WebMediaPicker() {
    fun pick(
        type: WebPickerType = WebPickerType.ANY,
        accept: String? = null,
        onResult: (WebPickedFile?) -> Unit,
    )
}
