package com.aj.shared.security

import com.aj.shared.api.HttpClientProvider
import com.aj.shared.storage.SecureStorage
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Remote Kill-Switch Guard for EazyCmp.
 * Periodically checks remote URL or custom URL for blacklisted package names.
 * Throws IllegalStateException immediately if the application's package name is blocked.
 */
object EazyCmpKillSwitch {
    private const val KEY_PACKAGE_BLOCKED = "eazycmp_is_package_blocked"
    private const val KEY_BLOCKED_PACKAGES_CACHE = "eazycmp_blocked_packages_cache"

    // Default remote raw json endpoint on GitHub
    const val DEFAULT_BLACKLIST_URL =
        "https://raw.githubusercontent.com/Imajy/eazyCmp/master/blacklisted_packages.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val storage by lazy { SecureStorage() }

    private var currentPackageName: String = ""
    private var isBlockedCached: Boolean = false

    /**
     * Initialize the kill switch with package name and optional custom remote URL.
     */
    fun checkAndStart(packageName: String, remoteUrl: String = DEFAULT_BLACKLIST_URL) {
        if (packageName.isBlank()) return
        currentPackageName = packageName

        // 1. Check local persistent storage (if previously detected as blocked)
        try {
            if (storage.getBoolean(KEY_PACKAGE_BLOCKED, false)) {
                isBlockedCached = true
                triggerKill("Application package '$packageName' is permanently disabled by EazyCmp Kill-Switch.")
            }

            // Check cached blocked packages
            val cachedJson = storage.getString(KEY_BLOCKED_PACKAGES_CACHE, "")
            if (cachedJson.isNotBlank()) {
                val cachedConfig = json.decodeFromString<BlacklistConfig>(cachedJson)
                if (cachedConfig.blockedPackages.any { it.trim().equals(packageName.trim(), ignoreCase = true) }) {
                    isBlockedCached = true
                    triggerKill("Application package '$packageName' is blocked by EazyCmp policy (cached).")
                }
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (_: Exception) {}

        // 2. Fetch remote config asynchronously and update enforcement
        fetchRemoteConfig(packageName, remoteUrl)
    }

    private fun fetchRemoteConfig(packageName: String, remoteUrl: String) {
        scope.launch {
            try {
                val client = HttpClientProvider.client
                val responseText = client.get(remoteUrl).bodyAsText()

                val config = json.decodeFromString<BlacklistConfig>(responseText)

                // Save latest response in persistent secure storage
                try {
                    storage.putString(KEY_BLOCKED_PACKAGES_CACHE, responseText)
                } catch (_: Exception) {}

                val isBlocked = config.blockedPackages.any {
                    it.trim().equals(packageName.trim(), ignoreCase = true)
                }

                if (isBlocked) {
                    isBlockedCached = true
                    try {
                        storage.putBoolean(KEY_PACKAGE_BLOCKED, true)
                    } catch (_: Exception) {}

                    triggerKill("Application package '$packageName' is blocked by EazyCmp remote configuration.")
                }
            } catch (e: IllegalStateException) {
                throw e
            } catch (_: Exception) {
                // If offline or network error, rely on cached state
            }
        }
    }

    /**
     * Active guard check that can be called before executing library functions.
     */
    fun assertNotBlocked() {
        if (isBlockedCached) {
            triggerKill("Access denied: EazyCmp is disabled for package '$currentPackageName'.")
        }
    }

    private fun triggerKill(message: String) {
        throw IllegalStateException("🚨 [EazyCmp Security] $message")
    }
}
