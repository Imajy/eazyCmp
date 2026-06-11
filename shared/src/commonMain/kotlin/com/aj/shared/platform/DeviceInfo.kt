package com.aj.shared.platform

data class DeviceInfoSnapshot(
    val appVersion: String,
    val osVersion: String,
    val model: String,
)

expect fun readDeviceInfo(): DeviceInfoSnapshot

expect class DeviceInfoProvider() {
    fun snapshot(): DeviceInfoSnapshot
}
