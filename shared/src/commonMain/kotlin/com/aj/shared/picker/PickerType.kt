package com.aj.shared.picker

enum class PickerType {

    CAMERA,
    IMAGE,
    DOCUMENT

}

data class DocumentConfig(

    val mimeTypes: List<String>

)

data class PickedFile(

    val bytes: ByteArray,
    val fileName: String?,
    val mimeType: String?

)