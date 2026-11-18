package com.aj.shared.parser

/**
 * Minimal ZIP writer using **store** (method 0) only — no compressor dependency.
 * Enough to emit a valid `.xlsx` OOXML package.
 */
internal object ZipStoreWriter {

    fun write(entries: Map<String, ByteArray>): ByteArray {
        val locals = ArrayList<ByteArray>(entries.size)
        val central = ArrayList<ByteArray>(entries.size)
        var offset = 0
        for ((name, data) in entries) {
            val nameBytes = name.encodeToByteArray()
            val crc = Crc32.of(data).toInt()
            val local = buildLocal(nameBytes, data, crc)
            val cd = buildCentral(nameBytes, data, crc, offset)
            locals += local
            central += cd
            offset += local.size
        }
        val cdStart = offset
        var cdSize = 0
        for (c in central) cdSize += c.size
        val eocd = ByteArray(22)
        writeLeInt(eocd, 0, 0x06054b50)
        writeLeShort(eocd, 4, 0) // disk
        writeLeShort(eocd, 6, 0)
        writeLeShort(eocd, 8, entries.size)
        writeLeShort(eocd, 10, entries.size)
        writeLeInt(eocd, 12, cdSize)
        writeLeInt(eocd, 16, cdStart)
        writeLeShort(eocd, 20, 0) // comment

        val total = offset + cdSize + eocd.size
        val out = ByteArray(total)
        var p = 0
        for (l in locals) {
            l.copyInto(out, p); p += l.size
        }
        for (c in central) {
            c.copyInto(out, p); p += c.size
        }
        eocd.copyInto(out, p)
        return out
    }

    private fun buildLocal(name: ByteArray, data: ByteArray, crc: Int): ByteArray {
        val out = ByteArray(30 + name.size + data.size)
        writeLeInt(out, 0, 0x04034b50)
        writeLeShort(out, 4, 20) // version needed
        writeLeShort(out, 6, 0) // flags
        writeLeShort(out, 8, 0) // store
        writeLeShort(out, 10, 0) // time
        writeLeShort(out, 12, 0) // date
        writeLeInt(out, 14, crc)
        writeLeInt(out, 18, data.size)
        writeLeInt(out, 22, data.size)
        writeLeShort(out, 26, name.size)
        writeLeShort(out, 28, 0) // extra
        name.copyInto(out, 30)
        data.copyInto(out, 30 + name.size)
        return out
    }

    private fun buildCentral(name: ByteArray, data: ByteArray, crc: Int, localOffset: Int): ByteArray {
        val out = ByteArray(46 + name.size)
        writeLeInt(out, 0, 0x02014b50)
        writeLeShort(out, 4, 20) // version made by
        writeLeShort(out, 6, 20) // version needed
        writeLeShort(out, 8, 0)
        writeLeShort(out, 10, 0) // store
        writeLeShort(out, 12, 0)
        writeLeShort(out, 14, 0)
        writeLeInt(out, 16, crc)
        writeLeInt(out, 20, data.size)
        writeLeInt(out, 24, data.size)
        writeLeShort(out, 28, name.size)
        writeLeShort(out, 30, 0)
        writeLeShort(out, 32, 0)
        writeLeShort(out, 34, 0)
        writeLeShort(out, 36, 0)
        writeLeInt(out, 38, 0)
        writeLeInt(out, 42, localOffset)
        name.copyInto(out, 46)
        return out
    }

    private fun writeLeShort(buf: ByteArray, i: Int, v: Int) {
        buf[i] = (v and 0xff).toByte()
        buf[i + 1] = ((v ushr 8) and 0xff).toByte()
    }

    private fun writeLeInt(buf: ByteArray, i: Int, v: Int) {
        buf[i] = (v and 0xff).toByte()
        buf[i + 1] = ((v ushr 8) and 0xff).toByte()
        buf[i + 2] = ((v ushr 16) and 0xff).toByte()
        buf[i + 3] = ((v ushr 24) and 0xff).toByte()
    }
}
