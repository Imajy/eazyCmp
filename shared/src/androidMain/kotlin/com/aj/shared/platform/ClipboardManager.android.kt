package com.aj.shared.platform

import android.content.ClipData
import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context
import com.aj.shared.api.appContext

actual class ClipboardManager actual constructor() {
    private val clipboard: AndroidClipboardManager
        get() = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager

    actual fun copy(text: String, label: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    actual fun paste(): String? =
        clipboard.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString()

    actual fun hasText(): Boolean =
        clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount?.let { it > 0 } == true
}
