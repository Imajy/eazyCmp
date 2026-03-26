package com.aj.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import coil3.svg.SvgDecoder
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import test.shared.generated.resources.Res

@Composable
fun CustomImage(
    model: Any? = null,
    placeholderJson: String = "loading.json",
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
) {

    val context = LocalPlatformContext.current

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    // 🔥 NULL / EMPTY
    if (model == null || (model is String && model.isBlank())) {
        LottiePlaceholder(placeholderJson, modifier, contentScale)
        return
    }

    when (model) {
        is ByteArray -> {

                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model) // 🔥 pass byte array directly
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = contentDescription,
                    modifier = modifier,
                    contentScale = contentScale
                ) {

                    val state by painter.state.collectAsState()

                    when (state) {

                        is AsyncImagePainter.State.Success -> {
                            SubcomposeAsyncImageContent()
                        }

                        is AsyncImagePainter.State.Loading -> {
                            LottiePlaceholder(
                                placeholderJson,
                                Modifier.matchParentSize(),
                                contentScale
                            )
                        }

                        is AsyncImagePainter.State.Error -> {

                            val error = (state as AsyncImagePainter.State.Error)
                                .result.throwable

                            println("❌ BYTE ERROR: $error")

                            LottiePlaceholder(
                                placeholderJson,
                                Modifier.matchParentSize(),
                                contentScale
                            )
                        }

                        else -> {
                            LottiePlaceholder(
                                placeholderJson,
                                Modifier.matchParentSize(),
                                contentScale
                            )
                        }
                    }
                }

                return
            }

        is Painter -> {
            Image(
                painter = model,
                contentDescription = contentDescription,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale,
                alpha = alpha,
                colorFilter = colorFilter
            )
        }

        is ImageVector -> {
            Image(
                imageVector = model,
                contentDescription = contentDescription,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale,
                alpha = alpha,
                colorFilter = colorFilter
            )
        }

        is String -> {

            val rawUrl = model.trim()

            // 🔥 FIX: unescape URL
            val cleanUrl = rawUrl.replace("\\/", "/")

            val isUrl = cleanUrl.startsWith("http")
            val isJson = cleanUrl.endsWith(".json", true)

            println("RAW URL => $rawUrl")
            println("CLEAN URL => $cleanUrl")

            // 🔹 LOCAL LOTTIE
            if (!isUrl && isJson) {
                LottiePlaceholder(cleanUrl, modifier, contentScale)
                return
            }

            // 🔥 URL IMAGE
            if (isUrl) {
                val cacheKey = cleanUrl.extractS3ImagePath() ?: cleanUrl
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(cleanUrl)
                        .memoryCacheKey(cacheKey).diskCacheKey(cacheKey)
                        .crossfade(true).diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED).scale(Scale.FILL)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = contentDescription,
                    modifier = modifier,
                    contentScale = contentScale
                ) {

                    val state by painter.state.collectAsState()

                    println("STATE => $state")

                    when (state) {

                        is AsyncImagePainter.State.Success -> {
                            SubcomposeAsyncImageContent()
                        }

                        is AsyncImagePainter.State.Loading -> {
                            LottiePlaceholder(
                                placeholderJson,
                                Modifier.matchParentSize(),
                                contentScale
                            )
                        }

                        is AsyncImagePainter.State.Error -> {
                            val error = (state as AsyncImagePainter.State.Error).result.throwable
                            println("❌ ERROR: $error")

                            LottiePlaceholder(
                                placeholderJson,
                                Modifier.matchParentSize(),
                                contentScale
                            )
                        }

                        else -> {
                            LottiePlaceholder(
                                placeholderJson,
                                Modifier.matchParentSize(),
                                contentScale
                            )
                        }
                    }
                }

                return
            }

            // 🔹 LOCAL IMAGE
            AsyncImage(
                model = Res.getUri("drawable/$cleanUrl"),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
    }
}


@Composable
fun LottiePlaceholder(
    jsonFile: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {

    val jsonString by produceState<String?>(initialValue = null, jsonFile) {
        value = try {
            Res.readBytes("drawable/$jsonFile").decodeToString()
        } catch (e: Exception) {
            println("❌ Lottie error: $e")
            null
        }
    }

    val composition by rememberLottieComposition {
        jsonString?.let { LottieCompositionSpec.JsonString(it) }
            ?: LottieCompositionSpec.JsonString("{}")
    }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = Int.MAX_VALUE
    )

    Image(
        painter = rememberLottiePainter(
            composition = composition,
            progress = { progress }
        ),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale
    )
}


fun String.extractS3ImagePath(): String? {
    return try {
        val crmIndex = indexOf("/crm/")
        if (crmIndex == -1) return null

        substring(crmIndex + "/crm/".length)
    } catch (_: Exception) {
        null
    }
}
