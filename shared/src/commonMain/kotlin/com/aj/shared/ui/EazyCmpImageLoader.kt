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

    fun normalizeCacheKey(value: String): String = value.trim().replace("\\/", "/")

    private fun isSvgSource(path: String?): Boolean =
        path?.endsWith(".svg", ignoreCase = true) == true

    private fun isSvgBytes(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val prefix = bytes.decodeToString(0, min(bytes.size, 256))
        return prefix.contains("<svg", ignoreCase = true)
    }
}
