package com.aj.shared.api

import com.aj.shared.picker.PickedFile

enum class ApiPriority {
    HIGH,
    NORMAL,
    LOW
}

data class RequestOptions(
    val priority: ApiPriority = ApiPriority.NORMAL,
    val retryOnConnection: Boolean = false
)



enum class BodyType {
    JSON,
    FORM_DATA,
    FORM_URLENCODED
}

data class FilePart(
    val name: String,
    val file: PickedFile?
)