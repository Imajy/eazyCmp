package com.aj.shared.permission

import androidx.compose.runtime.Composable

actual class PermissionManager actual constructor() {

    @Composable
    actual fun RegisterPermissionLauncher() {}

    actual suspend fun requestPermissions(
        permissions: List<AppPermission>,
        callback: PermissionCallback
    ) {
        val results = permissions.map { permission ->
            PermissionResult(permission, statusFor(permission))
        }
        callback.onResult(results)
    }

    private fun statusFor(permission: AppPermission): PermissionStatus = when (permission) {
        AppPermission.GALLERY,
        AppPermission.STORAGE,
        AppPermission.NOTIFICATION -> PermissionStatus.NOT_REQUIRED

        AppPermission.CAMERA -> webcamStatus()

        AppPermission.MICROPHONE,
        AppPermission.LOCATION,
        AppPermission.CONTACTS -> PermissionStatus.UNAVAILABLE
    }

    private fun webcamStatus(): PermissionStatus {
        return try {
            val webcamClass = Class.forName("com.github.sarxos.webcam.Webcam")
            val default = webcamClass.getMethod("getDefault").invoke(null)
            if (default != null) PermissionStatus.GRANTED else PermissionStatus.DENIED
        } catch (_: ClassNotFoundException) {
            PermissionStatus.UNAVAILABLE
        } catch (_: Exception) {
            PermissionStatus.DENIED
        }
    }
}
