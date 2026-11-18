package com.aj.shared.parser

import java.nio.charset.Charset

internal actual object PlatformCharset {
    actual fun decode(bytes: ByteArray, charsetName: String): String? =
        try {
            String(bytes, Charset.forName(charsetName))
        } catch (_: Exception) {
            null
        }
}
