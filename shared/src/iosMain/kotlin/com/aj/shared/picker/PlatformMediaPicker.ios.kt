package com.aj.shared.picker

import androidx.compose.runtime.Composable
import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.*
import platform.darwin.NSObject
import platform.posix.memcpy

actual class PlatformMediaPicker actual constructor() {

    private var callback: ((PickedFile?) -> Unit)? = null

    private var delegateRef: NSObject? = null

    @Composable
    actual fun RegisterLaunchers() {}

    actual fun launch(
        type: PickerType,
        documentConfig: DocumentConfig?,
        onResult: (PickedFile?) -> Unit
    ) {
        callback = onResult
        val controller = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return

        when(type) {
            PickerType.CAMERA -> openImagePicker(
                    controller,
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                )

            PickerType.IMAGE -> openImagePicker(
                controller,
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
             )

            PickerType.DOCUMENT -> openDocPicker(
                    controller,
                    documentConfig
                )
        }
    }


    private fun openImagePicker(
        controller: UIViewController,
        type: UIImagePickerControllerSourceType
    ) {

        val picker = UIImagePickerController()

        picker.sourceType = type


        val delegate = object :
                NSObject(),
                UIImagePickerControllerDelegateProtocol,
                UINavigationControllerDelegateProtocol {

                override fun imagePickerController(
                    picker: UIImagePickerController,
                    didFinishPickingMediaWithInfo:
                    Map<Any?, *>
                ) {

                    val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage

                    val data = image?.let {
                            UIImageJPEGRepresentation(
                                it,
                                0.8
                            )
                        }
                    val bytes = data?.toByteArray()

                    callback?.invoke(
                        bytes?.let {
                            PickedFile(
                                it,
                                "image_${NSDate().timeIntervalSince1970}.jpg",
                                "image/jpeg"
                            )
                        }
                    )
                    cleanup()
                    picker.dismissViewControllerAnimated(true, null)
                }

                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                    callback?.invoke(null)
                    cleanup()
                    picker.dismissViewControllerAnimated(true, null)
                }
            }

        delegateRef = delegate
        picker.delegate = delegate
        controller.presentViewController(picker, true, null)
    }

    private fun openDocPicker(
        controller: UIViewController,
        config: DocumentConfig?
    ) {
        val types = config?.mimeTypes
                ?.mapNotNull { UTType.typeWithMIMEType(it) } ?: listOf(UTTypeData)

        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)

        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {

                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>
                ) {
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL

                    if (url != null) {
                        url.startAccessingSecurityScopedResource()
                        val data = NSData.dataWithContentsOfURL(url)
                        val bytes = data?.toByteArray()
                        callback?.invoke(
                            bytes?.let {
                                PickedFile(
                                    it,
                                    url.lastPathComponent,
                                    null
                                )
                            }
                        )
                        url.stopAccessingSecurityScopedResource()
                    } else {
                        callback?.invoke(null)
                    }
                    cleanup()
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    callback?.invoke(null)
                    cleanup()
                }
            }

        delegateRef = delegate
        picker.delegate = delegate
        controller.presentViewController(
            picker,
            true,
            null
        )
    }

    private fun cleanup() { delegateRef = null }
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {

    val size = length.toInt()
    val bytes = ByteArray(size)
    bytes.usePinned {
        memcpy(
            it.addressOf(0),
            this.bytes,
            size.convert()
        )
    }
    return bytes
}