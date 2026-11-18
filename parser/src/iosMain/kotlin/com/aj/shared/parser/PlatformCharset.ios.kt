package com.aj.shared.parser

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.Foundation.NSData
import platform.Foundation.NSISOLatin1StringEncoding
import platform.Foundation.NSJapaneseEUCStringEncoding
import platform.Foundation.NSShiftJISStringEncoding
import platform.Foundation.NSString
import platform.Foundation.NSUTF16BigEndianStringEncoding
import platform.Foundation.NSUTF16LittleEndianStringEncoding
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSWindowsCP1250StringEncoding
import platform.Foundation.NSWindowsCP1251StringEncoding
import platform.Foundation.NSWindowsCP1252StringEncoding
import platform.Foundation.NSWindowsCP1253StringEncoding
import platform.Foundation.NSWindowsCP1254StringEncoding
import platform.Foundation.create

/**
 * iOS: NSString encodings + CFStringConvertEncoding for GBK/GB18030/Big5
 * (numeric CFStringEncoding constants — no CFSTR).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual object PlatformCharset {
    actual fun decode(bytes: ByteArray, charsetName: String): String? {
        val encoding = resolveEncoding(charsetName) ?: return null
        if (bytes.isEmpty()) return ""
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        @Suppress("CAST_NEVER_SUCCEEDS")
        return NSString.create(data = data, encoding = encoding) as String?
    }

    private fun resolveEncoding(charsetName: String): ULong? {
        val n = charsetName.trim().uppercase().replace('_', '-')
        return when (n) {
            "UTF-8", "UTF8" -> NSUTF8StringEncoding
            "UTF-16", "UTF-16LE", "UTF16", "UTF16LE" -> NSUTF16LittleEndianStringEncoding
            "UTF-16BE", "UTF16BE" -> NSUTF16BigEndianStringEncoding
            "WINDOWS-1250", "CP1250" -> NSWindowsCP1250StringEncoding
            "WINDOWS-1251", "CP1251" -> NSWindowsCP1251StringEncoding
            "WINDOWS-1252", "CP1252" -> NSWindowsCP1252StringEncoding
            "WINDOWS-1253", "CP1253" -> NSWindowsCP1253StringEncoding
            "WINDOWS-1254", "CP1254" -> NSWindowsCP1254StringEncoding
            "ISO-8859-1", "LATIN1" -> NSISOLatin1StringEncoding
            "SHIFT-JIS", "SHIFT_JIS", "SJIS", "CSSHIFTJIS" -> NSShiftJISStringEncoding
            "EUC-JP", "EUCJP" -> NSJapaneseEUCStringEncoding
            // CFStringEncoding: GBK_95=0x0631, GB_18030_2000=0x0632, Big5=0x0A03, Big5_HKSCS_1999=0x0A06
            "GBK", "GB2312", "GB-2312", "CP936", "MS936" ->
                CFStringConvertEncodingToNSStringEncoding(0x0631u)
            "GB18030", "GB-18030" ->
                CFStringConvertEncodingToNSStringEncoding(0x0632u)
            "BIG5", "BIG-5", "CP950" ->
                CFStringConvertEncodingToNSStringEncoding(0x0A03u)
            "BIG5-HKSCS", "BIG5HKSCS" ->
                CFStringConvertEncodingToNSStringEncoding(0x0A06u)
            else -> null
        }
    }
}
