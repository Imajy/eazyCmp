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


/*
---------------------------------------
PLACEHOLDER TYPES
---------------------------------------
*/

sealed interface Placeholder {

    data class LottieUrl(
        val url: String
    ) : Placeholder

    data class LottieJson(
        val json: String
    ) : Placeholder

    data class LottieBytes(
        val bytes: ByteArray
    ) : Placeholder


    data class PainterResource(
        val painter: Painter
    ) : Placeholder


    data class VectorResource(
        val imageVector: ImageVector
    ) : Placeholder


    data class ImageUrl(
        val url: String
    ) : Placeholder

}


/*
---------------------------------------
MAIN IMAGE
---------------------------------------
*/

@Composable
fun CustomImage(

    model: Any?,

    modifier: Modifier = Modifier,

    contentDescription: String? = null,

    placeholder: Placeholder? = Placeholder.LottieUrl("https://lottie.host/a9be1300-ee73-471a-969d-6ebe32a5fb64/NT7azVsdv1.json"),

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


    /*
    ------------------------
    PLACEHOLDER RENDERER
    ------------------------
    */

    val showPlaceholder: @Composable () -> Unit = {

        when (placeholder) {

            is Placeholder.LottieUrl,
            is Placeholder.LottieJson,
            is Placeholder.LottieBytes ->

                LottiePlaceholder(

                    placeholder,

                    modifier,

                    contentScale

                )

            is Placeholder.PainterResource ->

                Image(

                    painter = placeholder.painter,

                    contentDescription = null,

                    modifier = modifier,

                    contentScale = contentScale

                )

            is Placeholder.VectorResource ->

                Image(

                    imageVector = placeholder.imageVector,

                    contentDescription = null,

                    modifier = modifier,

                    contentScale = contentScale

                )

            is Placeholder.ImageUrl ->

                AsyncImage(

                    model = placeholder.url,

                    contentDescription = null,

                    modifier = modifier,

                    contentScale = contentScale

                )

            null -> {}
        }

    }


    /*
    ------------------------
    NULL CASE
    ------------------------
    */

    if (model == null) {

        showPlaceholder()

        return

    }


    /*
    ------------------------
    MODEL TYPES
    ------------------------
    */

    when (model) {

        /*
        painter
        */

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


        /*
        vector
        */

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


        /*
        bytearray image
        */

        is ByteArray ->
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .decoderFactory(SvgDecoder.Factory())
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
                if (state is AsyncImagePainter.State.Success)
                    SubcomposeAsyncImageContent()
                else
                    showPlaceholder()
            }

        /*
        string url
        */
        is String -> {
            val cleanPath = model.trim().replace("\\/", "/")

            val isUrl = cleanPath.startsWith("http")

            val isJson = cleanPath.endsWith(".json", true)

            val looksLikeFile = cleanPath.contains(".")

            /*
            ----------------
            LOTTIE JSON URL
            ----------------
            */
            if (isJson && isUrl) {
                LottiePlaceholder(
                    Placeholder.LottieUrl(cleanPath),
                    modifier,
                    contentScale
                )
                return
            }

            /*
            ----------------
            NETWORK IMAGE
            (png / jpg / svg)
            ----------------
            */
            if (isUrl) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(cleanPath)
                        .decoderFactory(SvgDecoder.Factory())
                        .memoryCacheKey(cleanPath)
                        .diskCacheKey(cleanPath)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .scale(Scale.FIT)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = contentDescription,
                    modifier = modifier,
                    contentScale = contentScale
                ) {
                    val state by painter.state.collectAsState()
                    if (state is AsyncImagePainter.State.Success) SubcomposeAsyncImageContent()
                    else showPlaceholder()
                }
                return
            }

            /*
            ----------------
            LOCAL RESOURCE
            svg / png / jpg
            ----------------
            */
            if (looksLikeFile) {
                val bytes by produceState<ByteArray?>(
                    initialValue = null,
                    key1 = cleanPath
                ) {
                    value = try {
                        CustomImageResourceResolver
                            .resolveBytes
                            ?.invoke(cleanPath)
                    } catch (e: Exception) {
                        println("Image resolve failed: $cleanPath")
                        null
                    }
                }

                if (bytes != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(bytes)
                            .decoderFactory(SvgDecoder.Factory())
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = contentDescription,
                        modifier = modifier,
                        contentScale = contentScale
                    )
                    return
                }
            }

            /*
            ----------------
            FALLBACK
            ----------------
            */
            showPlaceholder()
        }


        else ->

            showPlaceholder()

    }

}


/*
---------------------------------------
LOTTIE
---------------------------------------
*/
@Composable
fun LottiePlaceholder(
    placeholder: Placeholder,
    modifier: Modifier,
    contentScale: ContentScale
) {
    val jsonString by produceState<String?>(null, placeholder) {
        value = try {
            when (placeholder) {
                is Placeholder.LottieUrl -> HttpClient().get(placeholder.url).bodyAsText()
                is Placeholder.LottieJson -> placeholder.json
                is Placeholder.LottieBytes -> placeholder.bytes.decodeToString()
                else -> null
            }
        } catch (e: Exception) {
            println("Lottie error: $e")
            null
        }
    }

    val composition by rememberLottieComposition(
        LottieCompositionSpec.JsonString(
            jsonString ?: "{}"
        )
    )

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



object CustomImageResourceResolver {
    /**
     * Project can override this
     * to provide bytes for local resources
     */
    var resolveBytes: (suspend (String) -> ByteArray?)? = null
}