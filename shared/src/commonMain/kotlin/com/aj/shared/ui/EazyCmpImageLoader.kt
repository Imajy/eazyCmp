package com.aj.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.aj.shared.getCacheDir
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.concurrent.Volatile
import kotlin.math.min

/** Shared Coil [ImageLoader] with eazyCmp disk + memory caching. */
object EazyCmpImageLoader {
    private const val DISK_CACHE_MAX_BYTES = 50L * 1024L * 1024L
    private const val MEMORY_CACHE_MAX_BYTES = 25L * 1024L * 1024L

    @Volatile
    private var sharedLoader: ImageLoader? = null

    fun create(context: PlatformContext): ImageLoader {
        val cacheDir = getCacheDir().toPath()
        FileSystem.SYSTEM.createDirectories(cacheDir)

        return ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(MEMORY_CACHE_MAX_BYTES)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir)
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            // Coil 3.x already ignores Cache-Control headers by default and always
            // writes responses to the disk cache, so S3's no-store / short max-age
            // headers don't stop images from surviving app restarts. Combined with the
            // stable cache key (query-param stripping), an image downloaded once is
            // served from cache on every subsequent load.
            .build()
    }

    fun get(context: PlatformContext): ImageLoader {
        sharedLoader?.let { return it }
        return create(context).also { loader ->
            sharedLoader = loader
            SingletonImageLoader.setSafe { ctx ->
                sharedLoader ?: create(ctx).also { sharedLoader = it }
            }
        }
    }

    @Composable
    fun remember(context: PlatformContext = LocalPlatformContext.current): ImageLoader {
        return remember { get(context) }
    }

    fun urlRequest(
        context: PlatformContext,
        url: String,
        cacheKey: String = normalizeCacheKey(url),
    ): ImageRequest {
        val cleanUrl = url.trim()
        val builder = ImageRequest.Builder(context)
            .data(cleanUrl)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .crossfade(false)
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)

        if (isSvgSource(cleanUrl)) {
            builder.decoderFactory(SvgDecoder.Factory())
        }
        return builder.build()
    }

    fun bytesRequest(
        context: PlatformContext,
        bytes: ByteArray,
        cacheKey: String,
        sourcePath: String? = null,
    ): ImageRequest {
        val builder = ImageRequest.Builder(context)
            .data(bytes)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .crossfade(false)
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)

        if (isSvgSource(sourcePath) || isSvgBytes(bytes)) {
            builder.decoderFactory(SvgDecoder.Factory())
        }
        return builder.build()
    }

    suspend fun executeUrl(
        context: PlatformContext,
        url: String,
        cacheKey: String = normalizeCacheKey(url),
    ): ImageResult = get(context).execute(urlRequest(context, url, cacheKey))

    /**
     * Volatile query parameters that change on every request (e.g. AWS S3 presigned
     * URL signatures / expiry). They must be excluded from the cache key, otherwise
     * the same image gets a different key each time and is re-downloaded endlessly.
     */
    private val VOLATILE_QUERY_PARAMS = setOf(
        "x-amz-algorithm",
        "x-amz-credential",
        "x-amz-date",
        "x-amz-expires",
        "x-amz-signedheaders",
        "x-amz-signature",
        "x-amz-security-token",
        "x-amz-content-sha256",
        "awsaccesskeyid",
        "signature",
        "expires",
        "policy",
        "token",
    )

    /**
     * Builds a stable cache key for an image source. For remote URLs, signing/expiry
     * query params are stripped so a presigned S3 URL always maps to the same key
     * (the actual network request still uses the full signed URL via [urlRequest]).
     */
    fun normalizeCacheKey(value: String): String {
        val cleaned = value.trim().replace("\\/", "/")
        val queryIndex = cleaned.indexOf('?')
        if (queryIndex < 0) return cleaned

        val base = cleaned.substring(0, queryIndex)
        val query = cleaned.substring(queryIndex + 1)
        if (query.isEmpty()) return base

        val stableParams = query.split('&').filter { param ->
            val name = param.substringBefore('=').lowercase()
            name.isNotEmpty() && name !in VOLATILE_QUERY_PARAMS
        }

        return if (stableParams.isEmpty()) base else base + "?" + stableParams.joinToString("&")
    }

    private fun isSvgSource(path: String?): Boolean =
        path?.endsWith(".svg", ignoreCase = true) == true

    private fun isSvgBytes(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val prefix = bytes.decodeToString(0, min(bytes.size, 256))
        return prefix.contains("<svg", ignoreCase = true)
    }
}
