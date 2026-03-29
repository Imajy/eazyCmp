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