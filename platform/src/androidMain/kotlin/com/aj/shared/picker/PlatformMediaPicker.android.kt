package com.aj.shared.picker

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import com.aj.shared.api.appContext
import com.aj.shared.api.isAppContextInitialized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

actual class PlatformMediaPicker actual constructor() {

    private val context by lazy {
        check(isAppContextInitialized()) {
            "Call EazyCmp.init(context) before using PlatformMediaPicker."
        }
        appContext
    }

    private var callback: ((PickedFile?) -> Unit)? = null

    private var tempCameraUri: Uri? = null

    private var launcher:
            ((PickerType, DocumentConfig?, (PickedFile?) -> Unit) -> Unit)? = null

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Composable
    actual fun RegisterLaunchers() {

        val imagePickerLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                handleUri(uri)
            }

        val docLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                handleUri(uri)
            }

        val cameraLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture()
            ) { success ->
                if (success) {
                    handleUri(tempCameraUri)
                } else {
                    callback?.invoke(null)
                }
            }


        LaunchedEffect(Unit) {

            launcher = { type, config, result ->

                callback = result

                when (type) {

                    PickerType.IMAGE ->
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )

                    PickerType.DOCUMENT ->
                        docLauncher.launch(
                            config?.mimeTypes
                                ?.toTypedArray()
                                ?: arrayOf("*/*")
                        )

                    PickerType.CAMERA -> {
                        val uri = createTempUri()
                        tempCameraUri = uri
                        cameraLauncher.launch(uri)
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
        val cb = callback
        ioScope.launch {
            val picked = try {
                val bytes = context.contentResolver
                    .openInputStream(uri)
                    ?.use { it.readBytes() }
                val mime = context.contentResolver.getType(uri)
                val name = getFileName(uri)
                bytes?.let {
                    PickedFile(
                        it,
                        name,
                        mime
                    )
                }
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main.immediate) {
                cb?.invoke(picked)
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        return context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0 || !cursor.moveToFirst()) return@use null
                cursor.getString(index)
            }
    }

    private fun createTempUri(): Uri {
        val file = File.createTempFile(
            "camera_",
            ".jpg",
            context.cacheDir
        )

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.eazycmp.camera",
            file
        )
    }
}
