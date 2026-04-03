package com.aj.shared.permission

fun interface PermissionCallback {

    fun onResult(

        results: List<PermissionResult>

    )

}