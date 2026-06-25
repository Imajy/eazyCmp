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
import okio.Path.Companion.toPath

/** Shared Coil [ImageLoader] with eazyCmp disk + memory caching. */
object EazyCmpImageLoader {
    private const val DISK_CACHE_MAX_BYTES = 50L * 1024L * 1024L

    private var cachedLoader: ImageLoader? = null
    private var cachedContext: PlatformContext? = null

    fun create(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(getCacheDir().toPath())
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            .build()
    }

    fun get(context: PlatformContext): ImageLoader {
        val existing = cachedLoader
        if (existing != null && cachedContext === context) return existing
        return create(context).also {
            cachedLoader = it
            cachedContext = context
        }
    }

    @Composable
    fun remember(context: PlatformContext = LocalPlatformContext.current): ImageLoader {
        return remember(context) { get(context) }
    }

    fun urlRequest(
        context: PlatformContext,
        url: String,
        cacheKey: String = url.trim(),
    ): ImageRequest {
        val cleanUrl = url.trim()
        return ImageRequest.Builder(context)
            .data(cleanUrl)
            .decoderFactory(SvgDecoder.Factory())
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    suspend fun executeUrl(
        context: PlatformContext,
        url: String,
        cacheKey: String = url.trim(),
    ): ImageResult = get(context).execute(urlRequest(context, url, cacheKey))
}
