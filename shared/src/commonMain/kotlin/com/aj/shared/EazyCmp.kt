package com.aj.shared

import com.aj.shared.analytics.EazyAnalytics
import com.aj.shared.analytics.EazyCrash
import com.aj.shared.analytics.NoOpEazyAnalytics
import com.aj.shared.analytics.NoOpEazyCrash
import com.aj.shared.api.EazyLogger
import com.aj.shared.auth.AppleAuth
import com.aj.shared.auth.GoogleAuth
import com.aj.shared.auth.GuestModeManager
import com.aj.shared.auth.MultiAccountManager
import com.aj.shared.deeplink.DeepLinkHandler
import com.aj.shared.display.DisplaySettingsManager
import com.aj.shared.haptic.HapticManager
import com.aj.shared.location.Geocoder
import com.aj.shared.location.LocationManager
import com.aj.shared.network.ApiResponseCache
import com.aj.shared.network.ConnectivityObserver
import com.aj.shared.network.OfflineQueueManager
import com.aj.shared.network.RequestDeduplicator
import com.aj.shared.notification.InAppNotificationStore
import com.aj.shared.notification.PushTokenManager
import com.aj.shared.permission.PermissionManager
import com.aj.shared.picker.PlatformMediaPicker
import com.aj.shared.platform.ClipboardManager
import com.aj.shared.platform.DeviceInfoProvider
import com.aj.shared.platform.QrGenerator
import com.aj.shared.platform.QrScanner
import com.aj.shared.security.AppLockManager
import com.aj.shared.security.BackgroundLockManager
import com.aj.shared.security.ConsentManager
import com.aj.shared.security.SessionTimeoutManager
import com.aj.shared.share.ShareManager
import com.aj.shared.storage.ApiCacheStorage
import com.aj.shared.storage.FormDraftManager
import com.aj.shared.storage.LocalDataStore
import com.aj.shared.storage.PreferencesStore
import com.aj.shared.storage.SecureStorage
import com.aj.shared.theme.ThemeManager
import com.aj.shared.ui.Placeholder
import com.aj.shared.update.UpdateChecker
import com.aj.shared.upload.UploadManager
import com.aj.shared.upload.UploadQueueManager

object EazyCmp {
    const val VERSION = "1.0.03-alpha-11"

    // --- Core platform services ---
    val location: LocationManager by lazy { LocationManager() }
    val permission: PermissionManager by lazy { PermissionManager() }
    val media: PlatformMediaPicker by lazy { PlatformMediaPicker() }
    val network: ConnectivityObserver by lazy { ConnectivityObserver() }
    val storage: SecureStorage by lazy { SecureStorage() }
    val haptics: HapticManager by lazy { HapticManager() }
    val share: ShareManager by lazy { ShareManager() }
    val geocoder: Geocoder = Geocoder

    // --- Display & theme ---
    val display: DisplaySettingsManager by lazy { DisplaySettingsManager() }
    val theme: ThemeManager by lazy { ThemeManager() }

    // --- Security ---
    val appLock: AppLockManager by lazy { AppLockManager() }
    val sessionTimeout: SessionTimeoutManager by lazy { SessionTimeoutManager() }
    val backgroundLock: BackgroundLockManager by lazy { BackgroundLockManager() }
    val consent: ConsentManager by lazy { ConsentManager() }

    // --- Storage & drafts ---
    val formDrafts: FormDraftManager by lazy { FormDraftManager() }
    val preferences: PreferencesStore by lazy { PreferencesStore() }
    val apiCache: ApiCacheStorage by lazy { ApiCacheStorage() }
    val localStore: LocalDataStore by lazy { LocalDataStore("default") }
    val responseCache: ApiResponseCache by lazy { ApiResponseCache() }

    // --- Network ---
    val offlineQueue: OfflineQueueManager by lazy { OfflineQueueManager() }
    val requestDeduplicator: RequestDeduplicator by lazy { RequestDeduplicator() }

    // --- Upload (compress + fast upload) ---
    val upload: UploadManager by lazy { UploadManager() }
    val uploadQueue: UploadQueueManager by lazy { UploadQueueManager() }

    // --- Navigation & deep links ---
    val deepLinks: DeepLinkHandler by lazy { DeepLinkHandler() }

    // --- Updates ---
    val updates: UpdateChecker by lazy { UpdateChecker() }

    // --- Platform utilities ---
    val clipboard: ClipboardManager by lazy { ClipboardManager() }
    val deviceInfo: DeviceInfoProvider by lazy { DeviceInfoProvider() }
    val qrGenerator: QrGenerator by lazy { QrGenerator() }
    val qrScanner: QrScanner by lazy { QrScanner() }

    // --- Auth ---
    val googleAuth: GoogleAuth by lazy { GoogleAuth() }
    val appleAuth: AppleAuth by lazy { AppleAuth() }
    val accounts: MultiAccountManager by lazy { MultiAccountManager() }
    val guestMode: GuestModeManager by lazy { GuestModeManager() }

    // --- Notifications ---
    val pushToken: PushTokenManager by lazy { PushTokenManager() }
    val notifications: InAppNotificationStore by lazy { InAppNotificationStore() }

    // --- Analytics (host provides implementation) ---
    var analytics: EazyAnalytics = NoOpEazyAnalytics
    var crashReporter: EazyCrash = NoOpEazyCrash

    // --- Placeholders ---
    var defaultImagePlaceholder: Placeholder = Placeholder.LottieUrl(
        "https://lottie.host/a9be1300-ee73-471a-969d-6ebe32a5fb64/NT7azVsdv1.json"
    )
    var defaultApiLoadingPlaceholder: Placeholder = Placeholder.LottieUrl(
        "https://letterhead.ajmonic.com/loading.json"
    )

    var isDebugEnabled: Boolean
        get() = EazyLogger.isDebugEnabled
        set(value) { EazyLogger.isDebugEnabled = value }

    fun init(context: Any? = null, settingsName: String = "eazy_cmp_prefs") {
        platformInit(context, settingsName)
    }
}

internal expect fun platformInit(context: Any?, settingsName: String)
internal expect fun getCacheDir(): String
