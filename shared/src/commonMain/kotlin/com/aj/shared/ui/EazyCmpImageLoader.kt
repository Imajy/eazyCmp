package com.aj.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.aj.shared.getCacheDir
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.math.min

/** Shared Coil [ImageLoader] with eazyCmp disk + memory caching. */
object EazyCmpImageLoader {
    private const val DISK_CACHE_MAX_BYTES = 50L * 1024L * 1024L

    fun create(context: PlatformContext): ImageLoader {
        val cacheDir = getCacheDir().toPath()
        FileSystem.SYSTEM.createDirectories(cacheDir)

        return ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir)
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            .build()
    }

    @Composable
    fun remember(context: PlatformContext = LocalPlatformContext.current): ImageLoader {
        return remember(context) { create(context) }
    }

    fun urlRequest(
        context: PlatformContext,
        url: String,
        cacheKey: String = url.trim(),
    ): ImageRequest {
        val cleanUrl = url.trim()
        val builder = ImageRequest.Builder(context)
            .data(cleanUrl)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)

        if (isSvgUrl(cleanUrl)) {
            builder.decoderFactory(SvgDecoder.Factory())
        }
        return builder.build()
    }

    fun bytesRequest(
        context: PlatformContext,
        bytes: ByteArray,
        cacheKey: String? = null,
    ): ImageRequest {
        val builder = ImageRequest.Builder(context)
            .data(bytes)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)

        cacheKey?.let {
            builder.memoryCacheKey(it)
            builder.diskCacheKey(it)
        }
        if (isSvgBytes(bytes)) {
            builder.decoderFactory(SvgDecoder.Factory())
        }
        return builder.build()
    }

    suspend fun executeUrl(
        context: PlatformContext,
        url: String,
        cacheKey: String = url.trim(),
    ): ImageResult = create(context).execute(urlRequest(context, url, cacheKey))

    private fun isSvgUrl(url: String): Boolean = url.endsWith(".svg", ignoreCase = true)

    private fun isSvgBytes(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val prefix = bytes.decodeToString(0, min(bytes.size, 256))
        return prefix.contains("<svg", ignoreCase = true)
    }
}
