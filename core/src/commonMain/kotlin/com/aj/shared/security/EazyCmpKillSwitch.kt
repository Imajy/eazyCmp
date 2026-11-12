package com.aj.shared.security

import com.aj.shared.api.HttpClientProvider
import com.aj.shared.storage.SecureStorage
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Remote Kill-Switch Guard for EazyCmp.
 * Marks the package as blocked when listed remotely; does not crash the process from background work.
 */
object EazyCmpKillSwitch {
    private const val KEY_PACKAGE_BLOCKED = "eazycmp_is_package_blocked"
    private const val KEY_BLOCKED_PACKAGES_CACHE = "eazycmp_blocked_packages_cache"

    const val DEFAULT_BLACKLIST_URL =
        "https://raw.githubusercontent.com/Imajy/eazyCmp/master/blacklisted_packages.json"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        println("[EazyCmpKillSwitch] ${throwable.message}")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val storage by lazy { SecureStorage() }

    private var currentPackageName: String = ""
    private var isBlockedCached: Boolean = false
    private val _blocked = MutableStateFlow(false)
    val blocked: StateFlow<Boolean> = _blocked.asStateFlow()

    private var onBlocked: ((String) -> Unit)? = null

    fun setOnBlockedListener(listener: ((String) -> Unit)?) {
        onBlocked = listener
    }

    fun isBlocked(): Boolean = isBlockedCached

    /**
     * Initialize the kill switch with package name and optional custom remote URL.
     */
    fun checkAndStart(packageName: String, remoteUrl: String = DEFAULT_BLACKLIST_URL) {
        if (packageName.isBlank()) return
        currentPackageName = packageName

        try {
            if (storage.getBoolean(KEY_PACKAGE_BLOCKED, false)) {
                markBlocked("Application package '$packageName' is permanently disabled by EazyCmp Kill-Switch.")
            }

            val cachedJson = storage.getString(KEY_BLOCKED_PACKAGES_CACHE, "")
            if (cachedJson.isNotBlank()) {
                val cachedConfig = json.decodeFromString<BlacklistConfig>(cachedJson)
                if (cachedConfig.blockedPackages.any { it.trim().equals(packageName.trim(), ignoreCase = true) }) {
                    markBlocked("Application package '$packageName' is blocked by EazyCmp policy (cached).")
                }
            }
        } catch (_: Exception) {
            // Storage may not be ready yet; rely on remote fetch.
        }

        fetchRemoteConfig(packageName, remoteUrl)
    }

    private fun fetchRemoteConfig(packageName: String, remoteUrl: String) {
        scope.launch {
            try {
                val client = HttpClientProvider.client
                val responseText = client.get(remoteUrl).bodyAsText()
                val config = json.decodeFromString<BlacklistConfig>(responseText)

                try {
                    storage.putString(KEY_BLOCKED_PACKAGES_CACHE, responseText)
                } catch (_: Exception) {
                }

                val isBlocked = config.blockedPackages.any {
                    it.trim().equals(packageName.trim(), ignoreCase = true)
                }

                if (isBlocked) {
                    try {
                        storage.putBoolean(KEY_PACKAGE_BLOCKED, true)
                    } catch (_: Exception) {
                    }
                    markBlocked("Application package '$packageName' is blocked by EazyCmp remote configuration.")
                }
            } catch (_: Exception) {
                // Offline or network error — rely on cached state.
            }
        }
    }

    /**
     * Guard used by ApiClient. Throws only when already marked blocked so callers can map to Resource.Error.
     */
    fun assertNotBlocked() {
        if (isBlockedCached) {
            throw KillSwitchException(
                "Access denied: EazyCmp is disabled for package '$currentPackageName'."
            )
        }
    }

    private fun markBlocked(message: String) {
        isBlockedCached = true
        _blocked.value = true
        println("🚨 [EazyCmp Security] $message")
        try {
            onBlocked?.invoke(message)
        } catch (_: Exception) {
        }
    }
}

class KillSwitchException(message: String) : IllegalStateException(message)
