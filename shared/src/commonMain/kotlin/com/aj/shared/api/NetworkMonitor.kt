package com.aj.shared.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NetworkMonitor {
    private val _connected = MutableStateFlow(true)
    val connected: StateFlow<Boolean> = _connected
    fun setConnected(value: Boolean) {
        _connected.value = value
    }
}

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

data class PickedFile(
    val bytes: ByteArray,
    val fileName: String?,
    val mimeType: String?
)