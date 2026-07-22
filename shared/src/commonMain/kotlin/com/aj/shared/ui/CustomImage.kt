package com.aj.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import com.aj.shared.EazyCmp
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.jetbrains.compose.resources.ExperimentalResourceApi
import com.github.imajy.shared.generated.resources.Res

sealed interface Placeholder {
    data class LottieUrl(val url: String) : Placeholder
    data class LottieJson(val json: String) : Placeholder
    data class LottieBytes(val bytes: ByteArray) : Placeholder
    data class PainterResource(val painter: Painter) : Placeholder
    data class VectorResource(val imageVector: ImageVector) : Placeholder
    data class ImageUrl(val url: String) : Placeholder
    data class LocalPath(val path: String) : Placeholder

    companion object {
        fun from(pathOrUrl: String): Placeholder {
            val clean = pathOrUrl.trim().replace("\\/", "/")
            val isUrl = clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)
            val isJson = clean.endsWith(".json", ignoreCase = true) || clean.startsWith("{")
            return when {
                isJson && isUrl -> LottieUrl(clean)
                isJson -> LottieJson(clean)
                isUrl -> ImageUrl(clean)
                else -> LocalPath(clean)
            }
        }

        fun from(source: Any?): Placeholder? {
            return when (source) {
                null -> null
                is Placeholder -> source
                is Painter -> PainterResource(source)
                is ImageVector -> VectorResource(source)
                is ByteArray -> LottieBytes(source)
                is String -> from(source)
                else -> null
            }
        }
    }
}

private fun Placeholder?.isLottie(): Boolean = when (this) {
    is Placeholder.LottieUrl,
    is Placeholder.LottieJson,
    is Placeholder.LottieBytes -> true
    else -> false
}

private fun isLocalAssetPath(path: String): Boolean {
    val clean = path.trim()
    return clean.startsWith("drawable/") ||
        clean.startsWith("files/") ||
        clean.contains(".") ||
        clean.startsWith("composeResources/")
}

@Composable
fun CustomImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholder: Placeholder? = EazyCmp.defaultImagePlaceholder,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
) {
    val isPreview = LocalInspectionMode.current
    if (isPreview && model != null) {
        PreviewImage(
            model = model,
            placeholder = placeholder,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
        )
        return
    }

    val context = LocalPlatformContext.current
    val imageLoader = EazyCmpImageLoader.remember(context)

    if (model == null) {
        PlaceholderContent(
            placeholder = placeholder,
            modifier = modifier,
            contentScale = contentScale,
            imageLoader = imageLoader,
        )
        return
    }

    when (model) {
        is Painter -> Image(
            painter = model,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
        )

        is ImageVector -> Image(
            imageVector = model,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
        )

        is ByteArray -> {
            val cacheKey = remember(model) { model.contentHashCode().toString() }
            val request = remember(cacheKey) {
                EazyCmpImageLoader.bytesRequest(context, model, cacheKey)
            }
            CachedAsyncImage(
                request = request,
                imageLoader = imageLoader,
                placeholder = placeholder,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
            )
        }

        is String -> {
            val cleanPath = model.trim().replace("\\/", "/")
            val isUrl = cleanPath.startsWith("http")
            val isJson = cleanPath.endsWith(".json", true)

            when {
                isJson -> {
                    if (isUrl) {
                        LottiePlaceholder(Placeholder.LottieUrl(cleanPath), modifier, contentScale)
                    } else {
                        LottiePlaceholder(Placeholder.LottieJson(cleanPath), modifier, contentScale)
                    }
                }

                isUrl -> {
                    val cacheKey = remember(cleanPath) { EazyCmpImageLoader.normalizeCacheKey(cleanPath) }
                    val request = remember(cacheKey) {
                        EazyCmpImageLoader.urlRequest(context, cleanPath, cacheKey)
                    }
                    CachedAsyncImage(
                        request = request,
                        imageLoader = imageLoader,
                        placeholder = placeholder,
                        contentDescription = contentDescription,
                        modifier = modifier,
                        contentScale = contentScale,
                        colorFilter = colorFilter,
                    )
                }

                isLocalAssetPath(cleanPath) -> {
                    val bytes by produceState<ByteArray?>(initialValue = null, key1 = cleanPath) {
                        value = resolveResourceBytes(cleanPath)
                    }

                    if (bytes != null) {
                        val cacheKey = remember(cleanPath) { EazyCmpImageLoader.normalizeCacheKey(cleanPath) }
                        val request = remember(cacheKey, bytes) {
                            EazyCmpImageLoader.bytesRequest(
                                context = context,
                                bytes = bytes!!,
                                cacheKey = cacheKey,
                                sourcePath = cleanPath,
                            )
                        }
                        CachedAsyncImage(
                            request = request,
                            imageLoader = imageLoader,
                            placeholder = placeholder,
                            contentDescription = contentDescription,
                            modifier = modifier,
                            contentScale = contentScale,
                            colorFilter = colorFilter,
                        )
                    } else {
                        PlaceholderContent(
                            placeholder = placeholder,
                            modifier = modifier,
                            contentScale = contentScale,
                            imageLoader = imageLoader,
                        )
                    }
                }

                else -> PlaceholderContent(
                    placeholder = placeholder,
                    modifier = modifier,
                    contentScale = contentScale,
                    imageLoader = imageLoader,
                )
            }
        }

        else -> PlaceholderContent(
            placeholder = placeholder,
            modifier = modifier,
            contentScale = contentScale,
            imageLoader = imageLoader,
        )
    }
}

/**
 * Cached Coil load. [AsyncImage] for static placeholders; slot-based [SubcomposeAsyncImage]
 * only for Lottie loading UI (no collectAsState — iOS Metal safe).
 */
@Composable
private fun CachedAsyncImage(
    request: ImageRequest,
    imageLoader: ImageLoader,
    placeholder: Placeholder?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
) {
    if (placeholder.isLottie()) {
        SubcomposeAsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = colorFilter,
            loading = {
                LottiePlaceholder(
                    placeholder = placeholder!!,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            },
            error = {
                StaticPlaceholderContent(
                    placeholder = placeholder,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            },
            success = { SubcomposeAsyncImageContent() },
        )
        return
    }

    AsyncImage(
        model = request,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
    )
}

@Composable
private fun PlaceholderContent(
    placeholder: Placeholder?,
    modifier: Modifier,
    contentScale: ContentScale,
    imageLoader: ImageLoader,
) {
    when (placeholder) {
        is Placeholder.LottieUrl,
        is Placeholder.LottieJson,
        is Placeholder.LottieBytes -> LottiePlaceholder(placeholder, modifier, contentScale)

        is Placeholder.PainterResource -> Image(
            painter = placeholder.painter,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )

        is Placeholder.VectorResource -> Image(
            imageVector = placeholder.imageVector,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )

        is Placeholder.ImageUrl -> {
            val context = LocalPlatformContext.current
            val cacheKey = remember(placeholder.url) {
                EazyCmpImageLoader.normalizeCacheKey(placeholder.url)
            }
            val request = remember(cacheKey, context) {
                EazyCmpImageLoader.urlRequest(context, placeholder.url, cacheKey)
            }
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = modifier,
                contentScale = contentScale,
            )
        }

        is Placeholder.LocalPath -> {
            val context = LocalPlatformContext.current
            val bytes by produceState<ByteArray?>(initialValue = null, key1 = placeholder.path) {
                value = resolveResourceBytes(placeholder.path)
            }

            if (bytes != null) {
                val cacheKey = remember(placeholder.path) { EazyCmpImageLoader.normalizeCacheKey(placeholder.path) }
                val request = remember(cacheKey, bytes) {
                    EazyCmpImageLoader.bytesRequest(
                        context = context,
                        bytes = bytes!!,
                        cacheKey = cacheKey,
                        sourcePath = placeholder.path,
                    )
                }
                AsyncImage(
                    model = request,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = contentScale,
                )
            } else {
                PlaceholderFallback(modifier)
            }
        }

        null -> {}
    }
}

@Composable
private fun StaticPlaceholderContent(
    placeholder: Placeholder?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    when (placeholder) {
        is Placeholder.PainterResource -> Image(
            painter = placeholder.painter,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )

        is Placeholder.VectorResource -> Image(
            imageVector = placeholder.imageVector,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )

        else -> PlaceholderFallback(modifier)
    }
}

@Composable
private fun PlaceholderFallback(modifier: Modifier) {
    Box(
        modifier = modifier.background(Color.LightGray.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ){}
}

@Composable
private fun PreviewImage(
    model: Any,
    placeholder: Placeholder?,
    contentDescription: String?,
    modifier: Modifier,
    alignment: Alignment,
    contentScale: ContentScale,
    alpha: Float,
    colorFilter: ColorFilter?,
) {
    when (model) {
        is Painter -> Image(
            painter = model,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
        )

        is ImageVector -> Image(
            imageVector = model,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
        )

        else -> when (placeholder) {
            is Placeholder.PainterResource -> Image(
                painter = placeholder.painter,
                contentDescription = null,
                modifier = modifier,
                contentScale = contentScale,
            )

            is Placeholder.VectorResource -> Image(
                imageVector = placeholder.imageVector,
                contentDescription = null,
                modifier = modifier,
                contentScale = contentScale,
            )

            else -> Box(
                modifier = modifier.background(Color.LightGray.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Preview Loading...", fontSize = 10.sp, color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun LottiePlaceholder(
    placeholder: Placeholder,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val jsonString by produceState<String?>(null, placeholder) {
        value = try {
            when (placeholder) {
                is Placeholder.LottieUrl -> HttpClient().use { it.get(placeholder.url).bodyAsText() }
                is Placeholder.LottieJson -> {
                    val pathOrJson = placeholder.json
                    if (pathOrJson.endsWith(".json", ignoreCase = true) || isLocalAssetPath(pathOrJson)) {
                        val bytes = resolveResourceBytes(pathOrJson)
                        bytes?.decodeToString() ?: "{}"
                    } else {
                        pathOrJson
                    }
                }

                is Placeholder.LottieBytes -> placeholder.bytes.decodeToString()
                else -> null
            }
        } catch (e: Exception) {
            println("Lottie error: $e")
            null
        }
    }

    val composition by rememberLottieComposition(
        LottieCompositionSpec.JsonString(jsonString ?: "{}"),
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = Int.MAX_VALUE,
    )

    Image(
        painter = rememberLottiePainter(
            composition = composition,
            progress = { progress },
        ),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
    )
}

object CustomImageResourceResolver {
    var resolveBytes: (suspend (String) -> ByteArray?)? = null
}

@OptIn(ExperimentalResourceApi::class)
suspend fun resolveResourceBytes(path: String): ByteArray? {
    val customBytes = try {
        CustomImageResourceResolver.resolveBytes?.invoke(path)
    } catch (_: Exception) {
        null
    }
    if (customBytes != null) return customBytes

    val cleanPath = path.trim()
        .removePrefix("composeResources/")
        .substringAfter("resources/")

    val candidates = listOf(
        cleanPath,
        if (!cleanPath.startsWith("drawable/")) "drawable/$cleanPath" else cleanPath,
        if (!cleanPath.startsWith("files/")) "files/$cleanPath" else cleanPath
    ).distinct()

    for (candidate in candidates) {
        try {
            val bytes = Res.readBytes(candidate)
            if (bytes.isNotEmpty()) return bytes
        } catch (_: Exception) {
            // try next candidate
        }
    }
    return null
}
