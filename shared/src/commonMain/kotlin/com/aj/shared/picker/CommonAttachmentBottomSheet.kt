package com.aj.shared.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aj.shared.permission.AppPermission
import com.aj.shared.permission.PermissionManager
import com.aj.shared.permission.PermissionStatus
import com.aj.shared.ui.GenericBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonAttachmentBottomSheet(
    permissions: List<AppPermission>,
    onFilePicked: (PickedFile?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val picker = remember { PlatformMediaPicker() }
    picker.RegisterLaunchers()
    val permissionManager = remember { PermissionManager() }
    permissionManager.RegisterPermissionLauncher()
    GenericBottomSheet(
        show = true,
        onDismiss = {onFilePicked(null)},
        title = "Upload Document"
    ){
        AttachmentOptionsUI(
            showCamera = permissions.contains(AppPermission.CAMERA),
            showGallery = permissions.contains(AppPermission.GALLERY),
            showDocument = permissions.contains(AppPermission.STORAGE),
            onCamera = {
                scope.launch {
                    requestPermissionAndPick(
                        permissionManager,
                        AppPermission.CAMERA
                    ) { picker.launch(PickerType.CAMERA) { onFilePicked(it) } }
                }
            },
            onGallery = {
                scope.launch {
                    requestPermissionAndPick(
                        permissionManager,
                        AppPermission.GALLERY
                    ) {
                        picker.launch(PickerType.IMAGE) { onFilePicked(it) }
                    }
                }
            },
            onDocument = {
                scope.launch {
                    requestPermissionAndPick(
                        permissionManager,
                        AppPermission.STORAGE
                    ) {
                        picker.launch(
                            PickerType.DOCUMENT,
                            DocumentConfig(
                                mimeTypes = listOf(
                                    "application/pdf",
                                    "image/*"
                                )
                            )
                        ) { onFilePicked(it) }
                    }
                }
            }
        )
    }
}


suspend fun requestPermissionAndPick(
    manager: PermissionManager,
    permission: AppPermission,
    onGranted: () -> Unit
) {
    manager.requestPermissions(
        listOf(permission)
    ) { result ->
        val granted = result.firstOrNull()?.status == PermissionStatus.GRANTED
        if (granted) {
            onGranted()
        }
    }
}

@Composable
fun AttachmentOptionsUI(
    showCamera: Boolean,
    showGallery: Boolean,
    showDocument: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDocument: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        if (showCamera)
            AttachmentItem(
                "Camera",
                onCamera
            )

        if (showGallery)
            AttachmentItem(
                "Photos",
                onGallery
            )

        if (showDocument)
            AttachmentItem(
                "Document",
                onDocument
            )
    }
}


@Composable
fun AttachmentItem(
    title: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() }
    ) {
        Text(title)
    }
}