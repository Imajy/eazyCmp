package com.aj.shared.permission

enum class AppPermission {
    CAMERA,
    GALLERY,
    LOCATION,
    MICROPHONE,
    STORAGE,
    NOTIFICATION,
    CONTACTS;

    /** Permissions satisfied by a system picker — no runtime prompt on Android/iOS. */
    val isPickerOnly: Boolean
        get() = this == GALLERY || this == STORAGE
}

data class PermissionResult(

    val permission: AppPermission,
    val status: PermissionStatus

)

enum class PermissionStatus {

    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,

    /** Desktop / system picker — no runtime prompt. */
    NOT_REQUIRED,

    /** Not supported on this platform without a host plugin. */
    UNAVAILABLE

}

/** True when the feature may proceed (granted or no runtime prompt needed). */
val PermissionStatus.isUsable: Boolean
    get() = this == PermissionStatus.GRANTED || this == PermissionStatus.NOT_REQUIRED

fun PermissionResult.isUsable(): Boolean = status.isUsable