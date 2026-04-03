package com.aj.shared.permission

enum class AppPermission {
    CAMERA,
    GALLERY,
    LOCATION,
    MICROPHONE,
    STORAGE,
    NOTIFICATION,
    CONTACTS
}

data class PermissionResult(

    val permission: AppPermission,
    val status: PermissionStatus

)

enum class PermissionStatus {

    GRANTED,
    DENIED,
    PERMANENTLY_DENIED

}