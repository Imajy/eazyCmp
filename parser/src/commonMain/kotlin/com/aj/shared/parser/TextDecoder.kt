package com.aj.shared.parser

/**
 * Charset decode — custom BOM/UTF/1252 + platform charsets (Shift-JIS, GBK, …).
 */
internal object TextDecoder {

    fun decode(bytes: ByteArray, charsetName: String?): String {
        if (bytes.isEmpty()) return ""
        val forced = charsetName?.trim()?.takeIf { it.isNotEmpty() }
        if (forced != null) {
            PlatformCharset.decode(bytes, forced)?.let { return it.removePrefix("\uFEFF") }
            return fallbackForced(bytes, forced)
        }
        return autoDetect(bytes)
    }

    private fun fallbackForced(bytes: ByteArray, name: String): String {
        val n = name.uppercase().replace('_', '-')
        return when (n) {
            "UTF-8", "UTF8" -> bytes.decodeToString().removePrefix("\uFEFF")
            "UTF-16", "UTF-16LE", "UTF16", "UTF16LE" -> decodeUtf16(bytes, true)
            "UTF-16BE", "UTF16BE" -> decodeUtf16(bytes, false)
            "WINDOWS-1252", "CP1252", "ISO-8859-1", "LATIN1" -> decodeWindows1252(bytes)
            else -> bytes.decodeToString().removePrefix("\uFEFF")
        }
    }

    private fun autoDetect(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return bytes.copyOfRange(3, bytes.size).decodeToString()
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return decodeUtf16(bytes.copyOfRange(2, bytes.size), littleEndian = true)
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return decodeUtf16(bytes.copyOfRange(2, bytes.size), littleEndian = false)
            }
        }
        val utf8 = bytes.decodeToString()
        val sample = utf8.take(2048)
        val bad = sample.count { it == '\uFFFD' }
        if (bad > 8 && bad * 20 > sample.length) {
            PlatformCharset.decode(bytes, "Windows-1252")?.let { return it }
            return decodeWindows1252(bytes)
        }
        return utf8.removePrefix("\uFEFF")
    }

    private fun decodeUtf16(bytes: ByteArray, littleEndian: Boolean): String {
        val out = StringBuilder(bytes.size / 2)
        var i = 0
        while (i + 1 < bytes.size) {
            val code = if (littleEndian) {
                (bytes[i].toInt() and 0xff) or ((bytes[i + 1].toInt() and 0xff) shl 8)
            } else {
                ((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff)
            }
            out.append(code.toChar())
            i += 2
        }
        return out.toString().removePrefix("\uFEFF")
    }

    private fun decodeWindows1252(bytes: ByteArray): String {
        val map = windows1252Extras
        return buildString(bytes.size) {
            for (b in bytes) {
                val u = b.toInt() and 0xff
                if (u < 0x80 || u >= 0xA0) append(u.toChar()) else append(map[u - 0x80])
            }
        }
    }

    private val windows1252Extras: CharArray = charArrayOf(
        '\u20AC', '\u0081', '\u201A', '\u0192', '\u201E', '\u2026', '\u2020', '\u2021',
        '\u02C6', '\u2030', '\u0160', '\u2039', '\u0152', '\u008D', '\u017D', '\u008F',
        '\u0090', '\u2018', '\u2019', '\u201C', '\u201D', '\u2022', '\u2013', '\u2014',
        '\u02DC', '\u2122', '\u0161', '\u203A', '\u0153', '\u009D', '\u017E', '\u0178',
    )
}
