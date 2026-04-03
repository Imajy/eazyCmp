package com.aj.shared.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.aj.shared.api.appContext
import java.io.File

actual class PlatformMediaPicker actual constructor() {

    private val context = appContext

    private var callback: ((PickedFile?) -> Unit)? = null

    private var tempCameraUri: Uri? = null

    private var launcher:
            ((PickerType, DocumentConfig?, (PickedFile?) -> Unit) -> Unit)? = null


    @Composable
    actual fun RegisterLaunchers() {

        val galleryLauncher =
            rememberLauncherForActivityResult(

                ActivityResultContracts.OpenDocument()

            ) {

                handleUri(it)

            }

        val docLauncher =
            rememberLauncherForActivityResult(

                ActivityResultContracts.OpenDocument()

            ) {

                handleUri(it)

            }

        val cameraLauncher =
            rememberLauncherForActivityResult(

                ActivityResultContracts.TakePicture()

            ) { success ->

                if (success)

                    handleUri(tempCameraUri)

                else

                    callback?.invoke(null)

            }


        LaunchedEffect(Unit) {

            launcher = { type, config, result ->

                callback = result

                when(type) {

                    PickerType.IMAGE ->

                        galleryLauncher.launch(

                            arrayOf("image/*")

                        )

                    PickerType.DOCUMENT ->

                        docLauncher.launch(

                            config?.mimeTypes
                                ?.toTypedArray()
                                ?: arrayOf("*/*")

                        )

                    PickerType.CAMERA -> {

                        tempCameraUri = createTempUri()

                        cameraLauncher.launch(

                            tempCameraUri!!

                        )

                    }

                }

            }

        }

    }


    actual fun launch(
        type: PickerType,
        documentConfig: DocumentConfig?,
        onResult: (PickedFile?) -> Unit
    ) {
        launcher?.invoke(
            type,
            documentConfig,
            onResult
        )
    }

    private fun handleUri(uri: Uri?) {
        if (uri == null) {
            callback?.invoke(null)
            return
        }
        try {
            val bytes = context.contentResolver
                    .openInputStream(uri)
                    ?.use {
                        it.readBytes()
                    }
            val mime = context.contentResolver.getType(uri)
            val name = getFileName(uri)
            callback?.invoke(
                bytes?.let {
                    PickedFile(
                        it,
                        name,
                        mime
                    )
                }
            )
        } catch (e: Exception) {
            callback?.invoke(null)
        }
    }

    private fun getFileName(uri: Uri): String? {
        return context.contentResolver
            .query(uri,null,null,null,null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(index)
            }
    }

    private fun createTempUri(): Uri {

        val file = File.createTempFile(
                "camera_",
                ".jpg",
                context.cacheDir
            )

        return androidx.core.content.FileProvider
            .getUriForFile(context,
                "${context.packageName}.provider",
                file
            )
    }
}