package com.aj.shared.parser

/** Platform charset decode (JDK / Android Charset, iOS NSString) — not a 3rd-party lib. */
internal expect object PlatformCharset {
    /** @return null if charset unknown on this platform */
    fun decode(bytes: ByteArray, charsetName: String): String?
}
