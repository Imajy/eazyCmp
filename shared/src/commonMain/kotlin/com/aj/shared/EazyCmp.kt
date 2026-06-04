package com.aj.shared

import com.aj.shared.location.LocationManager
import com.aj.shared.permission.PermissionManager
import com.aj.shared.picker.PlatformMediaPicker
import com.aj.shared.network.ConnectivityObserver
import com.aj.shared.storage.SecureStorage
import com.aj.shared.ui.Placeholder
import com.aj.shared.haptic.HapticManager
import com.aj.shared.share.ShareManager

object EazyCmp {
    val location: LocationManager by lazy { LocationManager() }
    val permission: PermissionManager by lazy { PermissionManager() }
    val media: PlatformMediaPicker by lazy { PlatformMediaPicker() }
    val network: ConnectivityObserver by lazy { ConnectivityObserver() }
    val storage: SecureStorage by lazy { SecureStorage() }
    val haptics: HapticManager by lazy { HapticManager() }
    val share: ShareManager by lazy { ShareManager() }

    var defaultImagePlaceholder: Placeholder = Placeholder.LottieUrl("https://lottie.host/a9be1300-ee73-471a-969d-6ebe32a5fb64/NT7azVsdv1.json")
    var defaultApiLoadingPlaceholder: Placeholder = Placeholder.LottieUrl("https://letterhead.ajmonic.com/loading.json")

    fun init(context: Any? = null, settingsName: String = "eazy_cmp_prefs") {
        platformInit(context, settingsName)
    }
}

internal expect fun platformInit(context: Any?, settingsName: String)
internal expect fun getCacheDir(): String
