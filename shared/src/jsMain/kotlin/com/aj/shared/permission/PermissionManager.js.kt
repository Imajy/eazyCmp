package com.aj.shared.permission

import androidx.compose.runtime.Composable
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class PermissionManager actual constructor() {

    private var callback: PermissionCallback? = null

    @Composable
    actual fun RegisterPermissionLauncher() {
      // no launcher needed for web
    }

    actual suspend fun requestPermissions(
        permissions: List<AppPermission>,
        callback: PermissionCallback
    ) {
        this.callback = callback

        val results = permissions.map { permission ->
            val status = when (permission) {
                AppPermission.CAMERA -> triggerCameraPermission()
                AppPermission.LOCATION -> triggerLocationPermission()
                AppPermission.MICROPHONE -> triggerMicrophonePermission()
                else -> PermissionStatus.GRANTED // Storage/Gallery/Contacts web pe direct allowed hote hain via input tags
            }
            PermissionResult(permission, status)
        }

        callback.onResult(results)
    }

    private suspend fun triggerCameraPermission(): PermissionStatus =
        suspendCancellableCoroutine { continuation ->
            window.navigator.asDynamic().mediaDevices.getUserMedia(js("{video: true}"))
                .then { stream: dynamic ->
                    stream.getTracks().forEach { track: dynamic -> track.stop() }
                    continuation.resume(PermissionStatus.GRANTED)
                }
                .catch { _: dynamic ->
                    continuation.resume(PermissionStatus.DENIED)
                }
        }

    private suspend fun triggerMicrophonePermission(): PermissionStatus =
        suspendCancellableCoroutine { continuation ->
            window.navigator.asDynamic().mediaDevices.getUserMedia(js("{audio: true}"))
                .then { stream: dynamic ->
                    stream.getTracks().forEach { track: dynamic -> track.stop() }
                    continuation.resume(PermissionStatus.GRANTED)
                }
                .catch { continuation.resume(PermissionStatus.DENIED) }
        }

    private suspend fun triggerLocationPermission(): PermissionStatus =
        suspendCancellableCoroutine { continuation ->
            val navigator = window.navigator.asDynamic()

            navigator.geolocation.getCurrentPosition(
                {
                    continuation.resume(PermissionStatus.GRANTED)
                },
                {
                    continuation.resume(PermissionStatus.DENIED)
                }
            )
        }
}