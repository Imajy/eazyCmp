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
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.aj.shared.EazyCmp
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

sealed interface Placeholder {
    data class LottieUrl(val url: String) : Placeholder
    data class LottieJson(val json: String) : Placeholder
    data class LottieBytes(val bytes: ByteArray) : Placeholder
    data class PainterResource(val painter: Painter) : Placeholder
    data class VectorResource(val imageVector: ImageVector) : Placeholder
    data class ImageUrl(val url: String) : Placeholder
}

private fun Placeholder?.isLottie(): Boolean = when (this) {
    is Placeholder.LottieUrl,
    is Placeholder.LottieJson,
    is Placeholder.LottieBytes -> true
    else -> false
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
            val looksLikeFile = cleanPath.contains(".")

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

                looksLikeFile -> {
                    val bytes by produceState<ByteArray?>(initialValue = null, key1 = cleanPath) {
                        value = try {
                            CustomImageResourceResolver.resolveBytes?.invoke(cleanPath)
                        } catch (_: Exception) {
                            null
                        }
                    }

                    if (bytes != null) {
                        val cacheKey = remember(cleanPath) { EazyCmpImageLoader.normalizeCacheKey(cleanPath) }
                        val request = remember(cacheKey, bytes) {
                            EazyCmpImageLoader.bytesRequest(context, bytes!!, cacheKey)
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
 * Cached image load — no [SubcomposeAsyncImage], no [collectAsState] (iOS Metal safe).
 * Cache hit → [AsyncImagePainter.State.Success] on first frame, placeholder skipped.
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
    val painter = rememberAsyncImagePainter(
        model = request,
        imageLoader = imageLoader,
    )
    val state = painter.state.value

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (state) {
            is AsyncImagePainter.State.Success -> Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                colorFilter = colorFilter,
            )

            is AsyncImagePainter.State.Error -> StaticPlaceholderContent(
                placeholder = placeholder,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )

            else -> {
                if (placeholder.isLottie()) {
                    LottiePlaceholder(
                        placeholder = placeholder!!,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                } else {
                    StaticPlaceholderContent(
                        placeholder = placeholder,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                }
            }
        }
    }
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

        null -> {}
    }
}

/** Static placeholder only — no nested async loads (crash safe). */
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
                    if (pathOrJson.endsWith(".json", ignoreCase = true)) {
                        val bytes = CustomImageResourceResolver.resolveBytes?.invoke(pathOrJson)
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
