package com.aj.shared.ui

import androidx.compose.foundation.Image
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
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import test.shared.generated.resources.Res


@Composable
fun CustomImage(
    model: Any? = null,
    placeholder: Placeholder? =
        Placeholder.LottieUrl(
            "https://assets10.lottiefiles.com/packages/lf20_jcikwtux.json"
        ),
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

    val showPlaceholder: @Composable () -> Unit = {

        when (placeholder) {

            is Placeholder.LottieUrl ->
                LottiePlaceholder(
                    placeholder.url,
                    modifier,
                    contentScale
                )

            is Placeholder.LottieFile ->
                LottiePlaceholder(
                    placeholder.fileName,
                    modifier,
                    contentScale
                )

            is Placeholder.Drawable ->

                AsyncImage(
                    model =
                        Res.getUri(
                            "drawable/${placeholder.fileName}"
                        ),
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = contentScale
                )

            is Placeholder.Painter ->

                Image(
                    painter = placeholder.painter,
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = contentScale
                )

            is Placeholder.ImageVector ->

                Image(
                    imageVector = placeholder.imageVector,
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = contentScale
                )

            null -> {}

        }

    }

    if (model == null) {

        showPlaceholder()

        return

    }

    when (model) {

        is Painter ->

            Image(
                painter = model,
                contentDescription = contentDescription,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale,
                alpha = alpha,
                colorFilter = colorFilter
            )

        is ImageVector ->

            Image(
                imageVector = model,
                contentDescription = contentDescription,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale,
                alpha = alpha,
                colorFilter = colorFilter
            )

        is ByteArray ->

            SubcomposeAsyncImage(

                model = model,

                imageLoader = imageLoader,

                contentDescription = contentDescription,

                modifier = modifier,

                contentScale = contentScale

            ) {

                val state by painter.state.collectAsState()

                when (state) {

                    is AsyncImagePainter.State.Success ->
                        SubcomposeAsyncImageContent()

                    else ->
                        showPlaceholder()

                }

            }

        is String -> {

            val cleanUrl =
                model.replace("\\/", "/")

            val isUrl =
                cleanUrl.startsWith("http")

            val isJson =
                cleanUrl.endsWith(".json", true)

            if (isJson) {

                LottiePlaceholder(
                    cleanUrl,
                    modifier,
                    contentScale
                )

                return

            }

            if (isUrl) {

                SubcomposeAsyncImage(

                    model =
                        ImageRequest.Builder(context)
                            .data(cleanUrl)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .scale(Scale.FILL)
                            .build(),

                    imageLoader = imageLoader,

                    contentDescription = contentDescription,

                    modifier = modifier,

                    contentScale = contentScale

                ) {

                    val state by painter.state.collectAsState()

                    when (state) {

                        is AsyncImagePainter.State.Success ->
                            SubcomposeAsyncImageContent()

                        else ->
                            showPlaceholder()

                    }

                }

                return

            }

            AsyncImage(

                model =
                    Res.getUri(
                        "drawable/$cleanUrl"
                    ),

                contentDescription = contentDescription,

                modifier = modifier,

                contentScale = contentScale

            )

        }

        else -> showPlaceholder()

    }

}

@Composable
fun LottiePlaceholder(
    jsonFile: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {

    val client = remember { HttpClient() }

    val jsonString by produceState<String?>(null, jsonFile) {

        value = try {

            if (jsonFile.startsWith("http")) {

                println("LOTTIE URL => $jsonFile")

                client
                    .get(jsonFile)
                    .bodyAsText()

            } else {

                Res.readBytes("files/$jsonFile")
                    .decodeToString()

            }

        } catch (e: Exception) {

            println("❌ Lottie load error: $e")

            null
        }
    }

    val composition by rememberLottieComposition(

        spec = jsonString?.let {

            LottieCompositionSpec.JsonString(it)

        }

            ?: LottieCompositionSpec.JsonString("{}")

    )

    val progress by animateLottieCompositionAsState(

        composition = composition,

        iterations = Int.MAX_VALUE

    )

    if (composition != null) {

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
}


sealed interface Placeholder {

    data class LottieUrl(
        val url: String
    ) : Placeholder

    data class LottieFile(
        val fileName: String
    ) : Placeholder

    data class Painter(
        val painter: androidx.compose.ui.graphics.painter.Painter
    ) : Placeholder

    data class ImageVector(
        val imageVector: androidx.compose.ui.graphics.vector.ImageVector
    ) : Placeholder

    data class Drawable(
        val fileName: String
    ) : Placeholder

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
