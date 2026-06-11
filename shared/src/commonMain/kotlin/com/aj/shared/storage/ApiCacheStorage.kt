package com.aj.shared.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Serializable
private data class CacheEntry(
    val body: String,
    val expiresAtEpochMs: Long,
)

/**
 * File-based cache for GET responses keyed by URL or custom key.
 */
class ApiCacheStorage(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val defaultTtl: Duration = 5.minutes,
) {
    private val dir = "${CacheFileIO.cacheDirectory()}/api_cache"

    fun get(key: String): String? {
        val raw = CacheFileIO.read(cachePath(key)) ?: return null
        val entry = runCatching { json.decodeFromString<CacheEntry>(raw) }.getOrNull() ?: return null
        if (Clock.System.now().toEpochMilliseconds() > entry.expiresAtEpochMs) {
            invalidate(key)
            return null
        }
        return entry.body
    }

    fun put(key: String, body: String, ttl: Duration = defaultTtl) {
        val entry = CacheEntry(
            body = body,
            expiresAtEpochMs = Clock.System.now().toEpochMilliseconds() + ttl.inWholeMilliseconds,
        )
        CacheFileIO.write(cachePath(key), json.encodeToString(entry))
    }

    fun invalidate(key: String) {
        CacheFileIO.delete(cachePath(key))
    }

    fun clearAll() {
        CacheFileIO.deleteAllIn(dir)
    }

    private fun cachePath(key: String): String {
        val safe = key.hashCode().toUInt().toString(16)
        return "$dir/$safe.cache"
    }
}
