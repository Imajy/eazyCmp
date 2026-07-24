package com.aj.shared.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiCacheStorageTest {

    @Test
    fun testPutAndGetCacheEntry() {
        val cache = ApiCacheStorage(maxCacheSizeBytes = 10 * 1024 * 1024L)
        cache.clearAll()

        val key = "GET:https://api.example.com/user"
        val url = "https://api.example.com/user"
        val reqBody = """{"id":123}"""
        val respBody = """{"name":"John Doe","status":"active"}"""

        cache.put(key, url, reqBody, respBody)

        val entry = cache.get(key)
        assertNotNull(entry)
        assertEquals(url, entry.url)
        assertEquals(reqBody, entry.requestBody)
        assertEquals(respBody, entry.responseBody)
        assertTrue(entry.timestampEpochMs > 0)

        cache.clearAll()
    }

    @Test
    fun testEvictionWhenExceedingMaxSize() {
        // Small limit for testing eviction: 200 bytes
        val smallCache = ApiCacheStorage(maxCacheSizeBytes = 200L)
        smallCache.clearAll()

        // Entry 1 (approx 80 bytes)
        smallCache.put(
            key = "item1",
            url = "https://api.com/1",
            requestBody = null,
            responseBody = "A".repeat(60)
        )

        // Entry 2 (approx 80 bytes)
        smallCache.put(
            key = "item2",
            url = "https://api.com/2",
            requestBody = null,
            responseBody = "B".repeat(60)
        )

        assertEquals(2, smallCache.getCacheCount())

        // Entry 3 (approx 80 bytes) -> pushes total over 200 bytes limit!
        smallCache.put(
            key = "item3",
            url = "https://api.com/3",
            requestBody = null,
            responseBody = "C".repeat(60)
        )

        // Oldest item1 should be evicted!
        assertNull(smallCache.get("item1"))
        assertNotNull(smallCache.get("item2"))
        assertNotNull(smallCache.get("item3"))

        smallCache.clearAll()
    }
}
