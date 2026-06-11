package com.aj.shared.network

import com.aj.shared.api.json
import com.aj.shared.storage.SecureStorage
import kotlinx.serialization.Serializable
import kotlin.time.Clock

private const val CACHE_INDEX_KEY = "eazy_cmp_api_cache_index"

@Serializable
private data class CacheEntry(
    val body: String,
    val expiresAt: Long,
)

class ApiResponseCache(
    private val storage: SecureStorage = SecureStorage(),
    private val keyPrefix: String = "api_cache_",
) {
    fun get(key: String): String? {
        val cacheKey = cacheStorageKey(key)
        val raw = storage.getString(cacheKey)
        if (raw.isBlank()) return null

        val entry = runCatching { json.decodeFromString<CacheEntry>(raw) }.getOrNull() ?: run {
            storage.remove(cacheKey)
            removeFromIndex(cacheKey)
            return null
        }

        if (entry.expiresAt <= Clock.System.now().toEpochMilliseconds()) {
            storage.remove(cacheKey)
            removeFromIndex(cacheKey)
            return null
        }

        return entry.body
    }

    fun put(key: String, body: String, ttlMs: Long) {
        val cacheKey = cacheStorageKey(key)
        val entry = CacheEntry(
            body = body,
            expiresAt = Clock.System.now().toEpochMilliseconds() + ttlMs,
        )
        storage.putString(cacheKey, json.encodeToString(entry))
        addToIndex(cacheKey)
    }

    fun evict(key: String) {
        val cacheKey = cacheStorageKey(key)
        storage.remove(cacheKey)
        removeFromIndex(cacheKey)
    }

    fun clearExpired() {
        loadIndex().forEach { cacheKey ->
            val raw = storage.getString(cacheKey)
            if (raw.isBlank()) {
                removeFromIndex(cacheKey)
                return@forEach
            }
            val entry = runCatching { json.decodeFromString<CacheEntry>(raw) }.getOrNull()
            if (entry == null || entry.expiresAt <= Clock.System.now().toEpochMilliseconds()) {
                storage.remove(cacheKey)
                removeFromIndex(cacheKey)
            }
        }
    }

    fun clearAll() {
        loadIndex().forEach { storage.remove(it) }
        storage.remove(CACHE_INDEX_KEY)
    }

    private fun cacheStorageKey(key: String): String = "$keyPrefix$key"

    private fun loadIndex(): Set<String> {
        val raw = storage.getString(CACHE_INDEX_KEY)
        if (raw.isBlank()) return emptySet()
        return runCatching { json.decodeFromString<Set<String>>(raw) }.getOrDefault(emptySet())
    }

    private fun saveIndex(keys: Set<String>) {
        storage.putString(CACHE_INDEX_KEY, json.encodeToString(keys))
    }

    private fun addToIndex(cacheKey: String) {
        val index = loadIndex().toMutableSet()
        index.add(cacheKey)
        saveIndex(index)
    }

    private fun removeFromIndex(cacheKey: String) {
        val index = loadIndex().toMutableSet()
        if (index.remove(cacheKey)) {
            saveIndex(index)
        }
    }
}
