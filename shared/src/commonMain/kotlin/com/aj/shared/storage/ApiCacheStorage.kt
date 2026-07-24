package com.aj.shared.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration

@Serializable
data class ApiCacheEntry(
    val key: String,
    val url: String,
    val requestBody: String? = null,
    val responseBody: String,
    val timestampEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val sizeBytes: Long = 0L
)

@Serializable
private data class ApiCacheIndexItem(
    val key: String,
    val timestampEpochMs: Long,
    val sizeBytes: Long
)

@Serializable
private data class ApiCacheIndex(
    val items: List<ApiCacheIndexItem> = emptyList()
)

/**
 * File-based 10MB text cache for API responses.
 * Stores request URL, request body, response body text, and timestamp.
 * Evicts oldest cached entries (earliest timestamp) when total size exceeds 10MB.
 */
class ApiCacheStorage(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val defaultTtl: Duration? = null,
    val maxCacheSizeBytes: Long = 10 * 1024 * 1024L // 10 MB limit
) {
    private val dir = "${CacheFileIO.cacheDirectory()}/api_cache"
    private val indexPath = "$dir/cache_index.json"

    fun get(key: String): ApiCacheEntry? {
        val raw = CacheFileIO.read(cachePath(key)) ?: return null
        val entry = runCatching { json.decodeFromString<ApiCacheEntry>(raw) }.getOrNull() ?: run {
            invalidate(key)
            return null
        }
        if (entry.expiresAtEpochMs != null && Clock.System.now().toEpochMilliseconds() > entry.expiresAtEpochMs) {
            invalidate(key)
            return null
        }
        return entry
    }

    fun getResponseBody(key: String): String? {
        return get(key)?.responseBody
    }

    fun put(
        key: String,
        url: String,
        requestBody: String? = null,
        responseBody: String,
        ttl: Duration? = defaultTtl
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val expiresAt = ttl?.let { now + it.inWholeMilliseconds }

        val contentBytes = (url + (requestBody ?: "") + responseBody).encodeToByteArray().size.toLong()
        if (contentBytes > maxCacheSizeBytes) return

        val entry = ApiCacheEntry(
            key = key,
            url = url,
            requestBody = requestBody,
            responseBody = responseBody,
            timestampEpochMs = now,
            expiresAtEpochMs = expiresAt,
            sizeBytes = contentBytes
        )

        val indexItems = loadIndex().toMutableList()

        val existingIndex = indexItems.indexOfFirst { it.key == key }
        if (existingIndex != -1) {
            val removed = indexItems.removeAt(existingIndex)
            CacheFileIO.delete(cachePath(removed.key))
        }

        var currentTotalSize = indexItems.sumOf { it.sizeBytes }
        while (currentTotalSize + contentBytes > maxCacheSizeBytes && indexItems.isNotEmpty()) {
            indexItems.sortBy { it.timestampEpochMs }
            val oldest = indexItems.removeAt(0)
            CacheFileIO.delete(cachePath(oldest.key))
            currentTotalSize -= oldest.sizeBytes
        }

        CacheFileIO.write(cachePath(key), json.encodeToString(entry))
        indexItems.add(ApiCacheIndexItem(key = key, timestampEpochMs = now, sizeBytes = contentBytes))
        saveIndex(indexItems)
    }

    fun invalidate(key: String) {
        val indexItems = loadIndex().toMutableList()
        val existingIndex = indexItems.indexOfFirst { it.key == key }
        if (existingIndex != -1) {
            indexItems.removeAt(existingIndex)
            saveIndex(indexItems)
        }
        CacheFileIO.delete(cachePath(key))
    }

    fun clearAll() {
        CacheFileIO.deleteAllIn(dir)
    }

    fun getTotalCacheSizeBytes(): Long {
        return loadIndex().sumOf { it.sizeBytes }
    }

    fun getCacheCount(): Int {
        return loadIndex().size
    }

    private fun cachePath(key: String): String {
        val safeKey = key.hashCode().toUInt().toString(16)
        return "$dir/$safeKey.cache"
    }

    private fun loadIndex(): List<ApiCacheIndexItem> {
        val raw = CacheFileIO.read(indexPath) ?: return emptyList()
        return runCatching { json.decodeFromString<ApiCacheIndex>(raw).items }.getOrDefault(emptyList())
    }

    private fun saveIndex(items: List<ApiCacheIndexItem>) {
        CacheFileIO.write(indexPath, json.encodeToString(ApiCacheIndex(items)))
    }
}
