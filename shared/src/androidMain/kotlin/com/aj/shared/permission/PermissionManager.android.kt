package com.aj.shared.permission

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*

actual class PermissionManager actual constructor() {
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var callback: PermissionCallback? = null

    @Composable
    actual fun RegisterPermissionLauncher() {
        launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                val list = result.map {
                        PermissionResult(
                            permission = it.key.toPermissionEnum(),
                            status = if (it.value) PermissionStatus.GRANTED
                                else PermissionStatus.DENIED
                        )
                    }
                callback?.onResult(list)
            }
    }

    actual suspend fun requestPermissions(
        permissions: List<AppPermission>,
        callback: PermissionCallback
    ) {
        this.callback = callback
        launcher?.launch(
            permissions.map { it.toAndroidPermission()
            }.toTypedArray()
        )
    }
}

fun AppPermission.toAndroidPermission(): String {

    return when(this) {

        AppPermission.CAMERA -> Manifest.permission.CAMERA

        AppPermission.GALLERY -> Manifest.permission.READ_MEDIA_IMAGES

        AppPermission.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION

        AppPermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO

        AppPermission.STORAGE -> Manifest.permission.READ_EXTERNAL_STORAGE

        AppPermission.NOTIFICATION -> Manifest.permission.POST_NOTIFICATIONS

        AppPermission.CONTACTS -> Manifest.permission.READ_CONTACTS
    }
}

fun String.toPermissionEnum(): AppPermission {

    return when(this) {

        Manifest.permission.CAMERA -> AppPermission.CAMERA

        Manifest.permission.READ_MEDIA_IMAGES -> AppPermission.GALLERY

        Manifest.permission.ACCESS_FINE_LOCATION -> AppPermission.LOCATION

        Manifest.permission.RECORD_AUDIO -> AppPermission.MICROPHONE

        Manifest.permission.READ_EXTERNAL_STORAGE -> AppPermission.STORAGE

        Manifest.permission.POST_NOTIFICATIONS -> AppPermission.NOTIFICATION

        Manifest.permission.READ_CONTACTS -> AppPermission.CONTACTS

        else -> AppPermission.STORAGE

    }

}