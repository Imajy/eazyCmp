package com.aj.shared

import com.aj.shared.location.LocationManager
import com.aj.shared.permission.PermissionManager
import com.aj.shared.picker.PlatformMediaPicker
import com.aj.shared.network.ConnectivityObserver
import com.aj.shared.storage.SecureStorage

object EazyCmp {
    val location: LocationManager by lazy { LocationManager() }
    val permission: PermissionManager by lazy { PermissionManager() }
    val media: PlatformMediaPicker by lazy { PlatformMediaPicker() }
    val network: ConnectivityObserver by lazy { ConnectivityObserver() }
    val storage: SecureStorage by lazy { SecureStorage() }

    fun init(context: Any? = null, settingsName: String = "eazy_cmp_prefs") {
        platformInit(context, settingsName)
    }
}

internal expect fun platformInit(context: Any?, settingsName: String)
