package com.aj.shared.parser

/** Custom CRC-32 (ISO HDLC / PNG/ZIP polynomial) — full-buffer fingerprint. */
object Crc32 {
    private val table: IntArray = IntArray(256).also { t ->
        for (i in 0 until 256) {
            var c = i
            repeat(8) {
                c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1
            }
            t[i] = c
        }
    }

    fun of(bytes: ByteArray): Long {
        var crc = -1
        for (b in bytes) {
            crc = table[(crc xor (b.toInt() and 0xff)) and 0xff] xor (crc ushr 8)
        }
        return (crc.inv().toLong() and 0xFFFFFFFFL)
    }

    fun fingerprint(bytes: ByteArray): String = "${bytes.size}:${of(bytes)}"
}
