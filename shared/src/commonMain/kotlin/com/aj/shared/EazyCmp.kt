package com.aj.shared

import com.aj.shared.analytics.EazyAnalytics
import com.aj.shared.analytics.EazyCrash
import com.aj.shared.api.ApiClient
import com.aj.shared.api.ApiConfig
import com.aj.shared.api.EazyLogger
import com.aj.shared.auth.AppleAuth
import com.aj.shared.auth.GoogleAuth
import com.aj.shared.auth.GuestModeManager
import com.aj.shared.auth.MultiAccountManager
import com.aj.shared.deeplink.DeepLinkHandler
import com.aj.shared.display.DisplaySettingsManager
import com.aj.shared.haptic.HapticManager
import com.aj.shared.internal.EazyCmpBuildInfo
import com.aj.shared.internal.ServiceLocator
import com.aj.shared.location.Geocoder
import com.aj.shared.location.LocationManager
import com.aj.shared.network.ApiResponseCache
import com.aj.shared.network.ConnectivityObserver
import com.aj.shared.network.EazySocketManager
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
import com.aj.shared.plugin.DefaultSecureStorageService
import com.aj.shared.plugin.EazyPlugin
import com.aj.shared.plugin.EazyRegistry
import com.aj.shared.plugin.EazyServiceCatalog
import com.aj.shared.plugin.OtpAutoReadService
import com.aj.shared.plugin.PushTokenService
import com.aj.shared.plugin.QrScannerService
import com.aj.shared.plugin.SecureStorageService
import com.aj.shared.plugin.ServiceStatus
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
import com.aj.shared.storage.SocketLogStorage
import com.aj.shared.theme.ThemeManager
import com.aj.shared.ui.Placeholder
import com.aj.shared.ui.UiDefaults
import com.aj.shared.update.UpdateChecker
import com.aj.shared.upload.UploadManager
import com.aj.shared.upload.UploadQueueManager

object EazyCmp {
    val VERSION: String get() = EazyCmpBuildInfo.VERSION

    private val registry = EazyRegistry()

    init {
        ServiceLocator.libraryVersion = VERSION
    }

    private fun syncLocator() {
        // Avoid eagerly constructing Koin-backed services (e.g. OfflineQueueManager) during install/tests.
        ServiceLocator.analytics = registry.analytics
        ServiceLocator.isDebugEnabled = isDebugEnabled
        ServiceLocator.libraryVersion = VERSION
        UiDefaults.imagePlaceholder = defaultImagePlaceholder
        UiDefaults.apiLoadingPlaceholder = defaultApiLoadingPlaceholder
    }

    private fun bindLocatorServices() {
        ServiceLocator.network = network
        ServiceLocator.apiCache = apiCache
        ServiceLocator.formDrafts = formDrafts
        ServiceLocator.preferences = preferences
        // offlineQueue is Koin-backed — bind lazily on first use, not during init/install.
        syncLocator()
    }

    // --- Core platform services ---
    val location: LocationManager by lazy(LazyThreadSafetyMode.PUBLICATION) { LocationManager() }
    val permission: PermissionManager by lazy(LazyThreadSafetyMode.PUBLICATION) { PermissionManager() }
    val media: PlatformMediaPicker by lazy(LazyThreadSafetyMode.PUBLICATION) { PlatformMediaPicker() }
    val network: ConnectivityObserver by lazy(LazyThreadSafetyMode.PUBLICATION) { ConnectivityObserver() }
    val storage: SecureStorage by lazy(LazyThreadSafetyMode.PUBLICATION) { SecureStorage() }
    val haptics: HapticManager by lazy(LazyThreadSafetyMode.PUBLICATION) { HapticManager() }
    val share: ShareManager by lazy(LazyThreadSafetyMode.PUBLICATION) { ShareManager() }
    val geocoder: Geocoder = Geocoder
    val gpsSmoother: com.aj.shared.location.GpsLocationSmoother by lazy(LazyThreadSafetyMode.PUBLICATION) {
        com.aj.shared.location.GpsLocationSmoother()
    }

    // --- Display & theme ---
    val display: DisplaySettingsManager by lazy(LazyThreadSafetyMode.PUBLICATION) { DisplaySettingsManager() }
    val theme: ThemeManager by lazy(LazyThreadSafetyMode.PUBLICATION) { ThemeManager() }

    // --- Security & Crypto ---
    val crypto: com.aj.shared.security.EazyCrypto = com.aj.shared.security.EazyCrypto
    val killSwitch: com.aj.shared.security.EazyCmpKillSwitch = com.aj.shared.security.EazyCmpKillSwitch
    val appLock: AppLockManager by lazy(LazyThreadSafetyMode.PUBLICATION) { AppLockManager() }
    val sessionTimeout: SessionTimeoutManager by lazy(LazyThreadSafetyMode.PUBLICATION) { SessionTimeoutManager() }
    val backgroundLock: BackgroundLockManager by lazy(LazyThreadSafetyMode.PUBLICATION) { BackgroundLockManager() }
    val consent: ConsentManager by lazy(LazyThreadSafetyMode.PUBLICATION) { ConsentManager() }

    // --- Storage & drafts ---
    val formDrafts: FormDraftManager by lazy(LazyThreadSafetyMode.PUBLICATION) { FormDraftManager() }
    val preferences: PreferencesStore by lazy(LazyThreadSafetyMode.PUBLICATION) { PreferencesStore() }
    val cart: com.aj.shared.storage.CartStateStore by lazy(LazyThreadSafetyMode.PUBLICATION) {
        com.aj.shared.storage.CartStateStore(preferences = preferences)
    }
    val apiCache: ApiCacheStorage by lazy(LazyThreadSafetyMode.PUBLICATION) { ApiCacheStorage() }
    val socketLogCache: SocketLogStorage by lazy(LazyThreadSafetyMode.PUBLICATION) { SocketLogStorage() }
    val localStore: LocalDataStore by lazy(LazyThreadSafetyMode.PUBLICATION) { LocalDataStore("default") }
    val responseCache: ApiResponseCache by lazy(LazyThreadSafetyMode.PUBLICATION) { ApiResponseCache() }

    // --- Network & Sockets ---
    val api: ApiClient by lazy(LazyThreadSafetyMode.PUBLICATION) { ApiClient() }
    val socket: EazySocketManager by lazy(LazyThreadSafetyMode.PUBLICATION) { EazySocketManager() }
    val offlineQueue: OfflineQueueManager by lazy(LazyThreadSafetyMode.PUBLICATION) { OfflineQueueManager() }
    val requestDeduplicator: RequestDeduplicator by lazy(LazyThreadSafetyMode.PUBLICATION) { RequestDeduplicator() }

    // --- Upload (compress + fast upload) ---
    val upload: UploadManager by lazy(LazyThreadSafetyMode.PUBLICATION) { UploadManager() }
    val uploadQueue: UploadQueueManager by lazy(LazyThreadSafetyMode.PUBLICATION) { UploadQueueManager() }

    // --- Navigation & deep links ---
    val deepLinks: DeepLinkHandler by lazy(LazyThreadSafetyMode.PUBLICATION) { DeepLinkHandler() }

    // --- Updates ---
    val updates: UpdateChecker by lazy(LazyThreadSafetyMode.PUBLICATION) { UpdateChecker() }

    // --- Platform utilities ---
    val clipboard: ClipboardManager by lazy(LazyThreadSafetyMode.PUBLICATION) { ClipboardManager() }
    val deviceInfo: DeviceInfoProvider by lazy(LazyThreadSafetyMode.PUBLICATION) { DeviceInfoProvider() }
    val qrGenerator: QrGenerator by lazy(LazyThreadSafetyMode.PUBLICATION) { QrGenerator() }

    /** Built-in platform scanner (often a stub). Prefer [qrScannerService] for host overrides. */
    val qrScanner: QrScanner by lazy(LazyThreadSafetyMode.PUBLICATION) { QrScanner() }

    // --- Auth ---
    val googleAuth: GoogleAuth by lazy(LazyThreadSafetyMode.PUBLICATION) { GoogleAuth() }
    val appleAuth: AppleAuth by lazy(LazyThreadSafetyMode.PUBLICATION) { AppleAuth() }
    val accounts: MultiAccountManager by lazy(LazyThreadSafetyMode.PUBLICATION) { MultiAccountManager() }
    val guestMode: GuestModeManager by lazy(LazyThreadSafetyMode.PUBLICATION) { GuestModeManager() }

    // --- Notifications ---
    /** Built-in push token manager (often a stub). Prefer [pushTokenService] for host overrides. */
    val pushToken: PushTokenManager by lazy(LazyThreadSafetyMode.PUBLICATION) { PushTokenManager() }
    val notifications: InAppNotificationStore by lazy(LazyThreadSafetyMode.PUBLICATION) { InAppNotificationStore() }

    // --- Host-overridable services (Plugin SPI) ---
    val qrScannerService: QrScannerService get() = registry.qrScanner
    val pushTokenService: PushTokenService get() = registry.pushToken
    val otpAutoReadService: OtpAutoReadService get() = registry.otpAutoRead

    /**
     * Prefer this for new code. Uses host [EazyRegistry.secureStorage] when installed,
     * otherwise wraps platform [storage].
     */
    val storageService: SecureStorageService
        get() = registry.secureStorage ?: DefaultSecureStorageService(storage)

    var analytics: EazyAnalytics
        get() = registry.analytics
        set(value) {
            registry.analytics = value
            ServiceLocator.analytics = value
        }

    var crashReporter: EazyCrash
        get() = registry.crashReporter
        set(value) {
            registry.crashReporter = value
        }

    /** Install a typed plugin that can replace any host-wirable service. */
    fun install(plugin: EazyPlugin) {
        plugin.install(registry)
        syncLocator()
    }

    /** DSL install — host can wire QR, push, OTP, storage, analytics, or arbitrary extras. */
    fun install(block: EazyRegistry.() -> Unit) {
        registry.apply(block)
        syncLocator()
    }

    fun <T : Any> registerService(key: String, service: T) {
        registry.register(key, service)
    }

    fun <T> getService(key: String): T? = registry.get(key)

    fun serviceCatalog(): List<ServiceStatus> = EazyServiceCatalog.snapshot(registry)

    // --- Placeholders ---
    var defaultImagePlaceholder: Placeholder = Placeholder.LottieUrl(
        "https://lottie.host/a9be1300-ee73-471a-969d-6ebe32a5fb64/NT7azVsdv1.json"
    )
    var defaultApiLoadingPlaceholder: Placeholder = Placeholder.LottieUrl(
        "https://letterhead.ajmonic.com/loading.json"
    )

    fun setDefaultApiLoadingPlaceholder(source: Any) {
        Placeholder.from(source)?.let {
            defaultApiLoadingPlaceholder = it
            UiDefaults.apiLoadingPlaceholder = it
        }
    }

    fun setDefaultImagePlaceholder(source: Any) {
        Placeholder.from(source)?.let {
            defaultImagePlaceholder = it
            UiDefaults.imagePlaceholder = it
        }
    }

    fun setBaseUrl(
        baseUrl: String,
        token: String? = null,
        tokenProvider: (() -> String?)? = null,
        name: String = ApiConfig.DEFAULT_BASE_NAME,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, String> = emptyMap(),
        bodyParams: Map<String, Any?> = emptyMap()
    ) {
        ApiConfig.registerBaseUrl(
            name = name,
            baseUrl = baseUrl,
            token = token,
            tokenProvider = tokenProvider,
            defaultHeaders = headers,
            defaultQueryParams = queryParams,
            defaultBodyParams = bodyParams
        )
    }

    fun setAuthToken(token: String, name: String = ApiConfig.DEFAULT_BASE_NAME) {
        ApiConfig.updateToken(name, token)
    }

    fun setDebug(enabled: Boolean) {
        isDebugEnabled = enabled
    }

    fun configure(block: EazyCmpConfigBuilder.() -> Unit) {
        val builder = EazyCmpConfigBuilder().apply(block)

        if (builder.baseUrl.isNotBlank()) {
            setBaseUrl(
                baseUrl = builder.baseUrl,
                token = builder.token,
                tokenProvider = builder.tokenProvider,
                headers = builder.headers,
                queryParams = builder.queryParams,
                bodyParams = builder.bodyParams
            )
        }

        builder.apiLoadingPlaceholder?.let { setDefaultApiLoadingPlaceholder(it) }
        builder.imagePlaceholder?.let { setDefaultImagePlaceholder(it) }
        builder.socketUrl?.let { socket.connect(it) }
        isDebugEnabled = builder.isDebugEnabled
    }

    /** One-liner bootstrap: init + configure + optional plugins. */
    fun bootstrap(
        context: Any? = null,
        settingsName: String = "eazy_cmp_prefs",
        plugins: List<EazyPlugin> = emptyList(),
        block: EazyCmpConfigBuilder.() -> Unit = {},
    ) {
        init(context = context, settingsName = settingsName)
        configure(block)
        plugins.forEach { install(it) }
    }

    var isDebugEnabled: Boolean
        get() = EazyLogger.isDebugEnabled
        set(value) {
            EazyLogger.isDebugEnabled = value
        }

    fun init(context: Any? = null, settingsName: String = "eazy_cmp_prefs") {
        platformInit(context, settingsName)
        bindLocatorServices()
    }
}

class EazyCmpConfigBuilder {
    var baseUrl: String = ""
    var token: String? = null
    var tokenProvider: (() -> String?)? = null
    var headers: Map<String, String> = emptyMap()
    var queryParams: Map<String, String> = emptyMap()
    var bodyParams: Map<String, Any?> = emptyMap()

    var socketUrl: String? = null
    var isDebugEnabled: Boolean = false

    var apiLoadingPlaceholder: Any? = null
    var imagePlaceholder: Any? = null
}

internal expect fun platformInit(context: Any?, settingsName: String)
