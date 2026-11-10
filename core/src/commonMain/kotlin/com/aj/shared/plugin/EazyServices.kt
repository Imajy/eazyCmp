package com.aj.shared.plugin

import com.aj.shared.analytics.EazyAnalytics
import com.aj.shared.analytics.EazyCrash

typealias AnalyticsService = EazyAnalytics

typealias CrashService = EazyCrash

/** Host/camera QR result callbacks — defined in core so adapters stay decoupled. */
fun interface QrScanCallback {
    fun onResult(text: String)
    fun onError(message: String) = Unit
    fun onCancelled() = Unit
}

fun interface OtpAutoReadListener {
    fun onOtpReceived(otp: String)
    fun onError(message: String) = Unit
}

interface QrScannerService {
    fun startScan(onResult: QrScanCallback)
    fun stopScan()
    val isAvailable: Boolean
    val capability: ServiceCapability
    val hostHint: String?
}

interface PushTokenService {
    suspend fun getToken(): String?
    fun onTokenRefresh(callback: (String) -> Unit)
    val capability: ServiceCapability
    val hostHint: String?
}

interface OtpAutoReadService {
    fun startListening(listener: OtpAutoReadListener)
    fun stopListening()
    val isAvailable: Boolean
    val capability: ServiceCapability
    val hostHint: String?
}

interface SecureStorageService {
    fun putString(key: String, value: String)
    fun getString(key: String, defaultValue: String = ""): String
    fun putBoolean(key: String, value: Boolean)
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun putInt(key: String, value: Int)
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun putLong(key: String, value: Long)
    fun getLong(key: String, defaultValue: Long = 0L): Long
    fun remove(key: String)
    fun clear()
    val capability: ServiceCapability
}

/**
 * Host-only permission bridge (Compose registration stays on platform PermissionManager).
 */
interface PermissionService {
    suspend fun requestPermissions(
        permissions: List<String>,
        onResult: (List<Pair<String, String>>) -> Unit,
    )
}

/** No-op defaults used until platform adapters / host plugins are installed. */
object HostRequiredQrScannerService : QrScannerService {
    override fun startScan(onResult: QrScanCallback) {
        onResult.onError(
            "QR scanning requires a host adapter. Use EazyCmp.install { qrScanner = CameraXQrScanner(...) } " +
                "or AndroidCameraXQrScanner / DesktopZxingQrScanner.",
        )
    }

    override fun stopScan() = Unit
    override val isAvailable: Boolean = false
    override val capability: ServiceCapability = ServiceCapability.HOST_REQUIRED
    override val hostHint: String =
        "Install CameraX (Android), AVFoundation (iOS), or ZXing (Desktop) via EazyCmp.install."
}

object HostRequiredPushTokenService : PushTokenService {
    override suspend fun getToken(): String? = null
    override fun onTokenRefresh(callback: (String) -> Unit) = Unit
    override val capability: ServiceCapability = ServiceCapability.HOST_REQUIRED
    override val hostHint: String =
        "Install FcmPushTokenService (Android) or ApnsPushTokenService (iOS) via EazyCmp.install."
}

object HostRequiredOtpAutoReadService : OtpAutoReadService {
    override fun startListening(listener: OtpAutoReadListener) {
        listener.onError("OTP auto-read requires host SMS Retriever / User Consent wiring.")
    }

    override fun stopListening() = Unit
    override val isAvailable: Boolean = false
    override val capability: ServiceCapability = ServiceCapability.HOST_REQUIRED
    override val hostHint: String =
        "Install SmsRetrieverOtpAutoRead (Android) via EazyCmp.install { otpAutoRead = ... }."
}

class DefaultSecureStorageService(
    private val putStringImpl: (String, String) -> Unit,
    private val getStringImpl: (String, String) -> String,
    private val putBooleanImpl: (String, Boolean) -> Unit,
    private val getBooleanImpl: (String, Boolean) -> Boolean,
    private val putIntImpl: (String, Int) -> Unit,
    private val getIntImpl: (String, Int) -> Int,
    private val putLongImpl: (String, Long) -> Unit,
    private val getLongImpl: (String, Long) -> Long,
    private val removeImpl: (String) -> Unit,
    private val clearImpl: () -> Unit,
) : SecureStorageService {
    constructor(delegate: com.aj.shared.storage.SecureStorage) : this(
        putStringImpl = delegate::putString,
        getStringImpl = delegate::getString,
        putBooleanImpl = delegate::putBoolean,
        getBooleanImpl = delegate::getBoolean,
        putIntImpl = delegate::putInt,
        getIntImpl = delegate::getInt,
        putLongImpl = delegate::putLong,
        getLongImpl = delegate::getLong,
        removeImpl = delegate::remove,
        clearImpl = delegate::clear,
    )

    override fun putString(key: String, value: String) = putStringImpl(key, value)
    override fun getString(key: String, defaultValue: String) = getStringImpl(key, defaultValue)
    override fun putBoolean(key: String, value: Boolean) = putBooleanImpl(key, value)
    override fun getBoolean(key: String, defaultValue: Boolean) = getBooleanImpl(key, defaultValue)
    override fun putInt(key: String, value: Int) = putIntImpl(key, value)
    override fun getInt(key: String, defaultValue: Int) = getIntImpl(key, defaultValue)
    override fun putLong(key: String, value: Long) = putLongImpl(key, value)
    override fun getLong(key: String, defaultValue: Long) = getLongImpl(key, defaultValue)
    override fun remove(key: String) = removeImpl(key)
    override fun clear() = clearImpl()
    override val capability: ServiceCapability = ServiceCapability.FULL
}
