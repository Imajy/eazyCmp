package com.aj.shared.parser

/**
 * Custom ZIP reader for XLSX — lazy entry inflate (only requested paths).
 * Supports ZIP64 EOCD; rejects encrypted packages; works over [BinarySource]
 * so large on-disk workbooks need not be fully loaded as [ByteArray].
 */
internal object ZipReader {

    private const val LOCAL_FILE = 0x04034b50
    private const val CENTRAL_DIR = 0x02014b50
    private const val END_CENTRAL = 0x06054b50
    private const val ZIP64_EOCD = 0x06064b50
    private const val ZIP64_LOCATOR = 0x07064b50

    fun open(bytes: ByteArray, isActive: () -> Boolean = { true }): ZipArchive =
        open(ByteArraySource(bytes), isActive, closeSource = false)

    fun open(source: BinarySource, isActive: () -> Boolean = { true }, closeSource: Boolean = true): ZipArchive =
        ZipArchive(source, indexCentralDirectory(source, isActive), isActive, closeSource)

    /** Eager read — prefer [open] + selective [ZipArchive.read] for large xlsx. */
    fun readEntries(
        bytes: ByteArray,
        isActive: () -> Boolean = { true },
    ): Map<String, ByteArray> {
        val archive = open(bytes, isActive)
        val out = linkedMapOf<String, ByteArray>()
        for (name in archive.names) {
            if (!isActive()) throw ParseCancelledException("ZIP parse cancelled")
            out[name] = archive.read(name) ?: continue
        }
        return out
    }

    internal data class EntryMeta(
        val name: String,
        val method: Int,
        val compressedSize: Int,
        val uncompressedSize: Int,
        val localOffset: Int,
    )

    private fun indexCentralDirectory(
        bytes: BinarySource,
        isActive: () -> Boolean,
    ): Map<String, EntryMeta> {
        val eocdInfo = findEndOfCentralDirectory(bytes)
            ?: throw IllegalArgumentException("Not a ZIP archive (missing EOCD)")
        var cdOffset = eocdInfo.cdOffset
        var totalEntries = eocdInfo.totalEntries

        if (cdOffset == 0xFFFFFFFFL || totalEntries == 0xFFFFL) {
            val zip64 = findZip64End(bytes, eocdInfo.eocdOffset)
                ?: throw IllegalArgumentException(
                    "ZIP64 archive detected but ZIP64 EOCD is missing/corrupt.",
                )
            cdOffset = zip64.cdOffset
            totalEntries = zip64.totalEntries
            if (cdOffset > Int.MAX_VALUE.toLong() || totalEntries > Int.MAX_VALUE.toLong()) {
                throw IllegalArgumentException(
                    "ZIP64 archive is too large for this parser (offset/entry count exceeds 2GB limit).",
                )
            }
        }
        if (cdOffset < 0 || cdOffset >= bytes.size) {
            throw IllegalArgumentException("Invalid ZIP central directory offset")
        }

        val out = linkedMapOf<String, EntryMeta>()
        var offset = cdOffset.toInt()
        var entryIndex = 0
        while (entryIndex < totalEntries) {
            if (!isActive()) throw ParseCancelledException("ZIP parse cancelled")
            require(offset + 46 <= bytes.size) { "Truncated ZIP central directory" }
            require(bytes.readLeInt(offset) == CENTRAL_DIR) {
                "Invalid central directory signature at $offset"
            }
            val method = bytes.readLeShort(offset + 10)
            var compressedSize = bytes.readLeInt(offset + 20).toLong() and 0xFFFFFFFFL
            var uncompressedSize = bytes.readLeInt(offset + 24).toLong() and 0xFFFFFFFFL
            val nameLen = bytes.readLeShort(offset + 28)
            val extraLen = bytes.readLeShort(offset + 30)
            val commentLen = bytes.readLeShort(offset + 32)
            var localOffset = bytes.readLeInt(offset + 42).toLong() and 0xFFFFFFFFL
            val nameStart = offset + 46
            val nameEnd = nameStart + nameLen
            require(nameEnd <= bytes.size) { "Truncated ZIP entry name" }
            val name = bytes.copyOfRange(nameStart, nameEnd).decodeToString()

            if (compressedSize == 0xFFFFFFFFL || uncompressedSize == 0xFFFFFFFFL || localOffset == 0xFFFFFFFFL) {
                val extra = bytes.copyOfRange(nameEnd, nameEnd + extraLen)
                parseZip64Extra(extra, compressedSize, uncompressedSize, localOffset).let {
                    compressedSize = it.first
                    uncompressedSize = it.second
                    localOffset = it.third
                }
            }

            offset = nameEnd + extraLen + commentLen
            entryIndex++

            val normalized = name.replace('\\', '/')
            if (normalized.endsWith("/")) continue

            if (normalized.equals("EncryptionInfo", ignoreCase = true) ||
                normalized.equals("EncryptedPackage", ignoreCase = true) ||
                normalized.endsWith("/EncryptionInfo", ignoreCase = true) ||
                normalized.endsWith("/EncryptedPackage", ignoreCase = true)
            ) {
                throw EncryptedWorkbookException(
                    "This Excel file is password-protected / encrypted (Office Open XML EncryptionInfo). " +
                        "This parser cannot decrypt passwords. " +
                        "Remove the password in Excel (File → Info → Protect → Encrypt) and save as normal .xlsx.",
                )
            }
            val critical = isCriticalWorkbookPart(normalized)
            if (method != 0 && method != 8) {
                if (critical) {
                    throw UnsupportedZipException(
                        "Unsupported ZIP compression method $method for $name " +
                            "(only store/deflate). Re-save the workbook as a normal .xlsx.",
                    )
                }
                continue
            }
            if (compressedSize > Int.MAX_VALUE || uncompressedSize > Int.MAX_VALUE) {
                if (critical) {
                    throw UnsupportedZipException(
                        "ZIP entry \"$name\" is too large (>2GB) for this parser.",
                    )
                }
                continue
            }
            out[normalized] = EntryMeta(
                name = normalized,
                method = method,
                compressedSize = compressedSize.toInt(),
                uncompressedSize = uncompressedSize.toInt(),
                localOffset = localOffset.toInt(),
            )
        }
        return out
    }

    internal fun inflateEntry(bytes: BinarySource, meta: EntryMeta): ByteArray {
        val localOffset = meta.localOffset
        require(localOffset + 30 <= bytes.size) { "Truncated local header for ${meta.name}" }
        require(bytes.readLeInt(localOffset) == LOCAL_FILE) {
            "Invalid local header for ${meta.name}"
        }
        val flags = bytes.readLeShort(localOffset + 6)
        if (flags and 0x1 != 0) {
            throw EncryptedWorkbookException(
                "ZIP entry \"${meta.name}\" is encrypted. Remove password protection and retry.",
            )
        }
        val nameLen = bytes.readLeShort(localOffset + 26)
        val extraLen = bytes.readLeShort(localOffset + 28)
        val dataStart = localOffset + 30 + nameLen + extraLen
        require(dataStart + meta.compressedSize <= bytes.size) { "Truncated data for ${meta.name}" }
        val compressed = bytes.copyOfRange(dataStart, dataStart + meta.compressedSize)
        return when (meta.method) {
            0 -> compressed
            8 -> DeflateInflater.inflate(compressed, meta.uncompressedSize)
            else -> throw UnsupportedZipException(
                "Unsupported ZIP compression method ${meta.method} for ${meta.name}",
            )
        }
    }

    private data class EocdInfo(val eocdOffset: Int, val cdOffset: Long, val totalEntries: Long)
    private data class Zip64Info(val cdOffset: Long, val totalEntries: Long)

    private fun findEndOfCentralDirectory(bytes: BinarySource): EocdInfo? {
        // Read a tail window once — avoids per-byte seeks on RAF-backed sources.
        val windowSize = minOf(bytes.size, 22 + 65535)
        if (windowSize < 22) return null
        val start = bytes.size - windowSize
        val window = bytes.copyOfRange(start, bytes.size)
        var i = window.size - 22
        while (i >= 0) {
            val sig = (window[i].toInt() and 0xff) or
                ((window[i + 1].toInt() and 0xff) shl 8) or
                ((window[i + 2].toInt() and 0xff) shl 16) or
                ((window[i + 3].toInt() and 0xff) shl 24)
            if (sig == END_CENTRAL) {
                val abs = start + i
                val totalEntries = ((window[i + 10].toInt() and 0xff) or
                    ((window[i + 11].toInt() and 0xff) shl 8)).toLong()
                val cdOffset = ((window[i + 16].toInt() and 0xff) or
                    ((window[i + 17].toInt() and 0xff) shl 8) or
                    ((window[i + 18].toInt() and 0xff) shl 16) or
                    ((window[i + 19].toInt() and 0xff) shl 24)).toLong() and 0xFFFFFFFFL
                return EocdInfo(abs, cdOffset, totalEntries)
            }
            i--
        }
        return null
    }

    private fun findZip64End(bytes: BinarySource, eocdOffset: Int): Zip64Info? {
        val locator = eocdOffset - 20
        if (locator < 0 || bytes.readLeInt(locator) != ZIP64_LOCATOR) return null
        val zip64EocdOffset = bytes.readLeLong(locator + 8)
        if (zip64EocdOffset < 0 || zip64EocdOffset + 56 > bytes.size) return null
        val off = zip64EocdOffset.toInt()
        if (bytes.readLeInt(off) != ZIP64_EOCD) return null
        return Zip64Info(bytes.readLeLong(off + 48), bytes.readLeLong(off + 32))
    }

    private fun isCriticalWorkbookPart(path: String): Boolean {
        val p = path.replace('\\', '/').lowercase()
        return p == "xl/workbook.xml" ||
            p == "xl/_rels/workbook.xml.rels" ||
            p == "xl/sharedstrings.xml" ||
            p == "xl/styles.xml" ||
            p.startsWith("xl/worksheets/")
    }

    private fun parseZip64Extra(
        extra: ByteArray,
        compressedSize: Long,
        uncompressedSize: Long,
        localOffset: Long,
    ): Triple<Long, Long, Long> {
        var c = compressedSize
        var u = uncompressedSize
        var o = localOffset
        var i = 0
        while (i + 4 <= extra.size) {
            val id = (extra[i].toInt() and 0xff) or ((extra[i + 1].toInt() and 0xff) shl 8)
            val size = (extra[i + 2].toInt() and 0xff) or ((extra[i + 3].toInt() and 0xff) shl 8)
            i += 4
            if (i + size > extra.size) break
            if (id == 0x0001) {
                var p = i
                if (u == 0xFFFFFFFFL && p + 8 <= i + size) {
                    u = extra.readLeLongLocal(p); p += 8
                }
                if (c == 0xFFFFFFFFL && p + 8 <= i + size) {
                    c = extra.readLeLongLocal(p); p += 8
                }
                if (o == 0xFFFFFFFFL && p + 8 <= i + size) {
                    o = extra.readLeLongLocal(p)
                }
            }
            i += size
        }
        return Triple(c, u, o)
    }

    private fun ByteArray.readLeLongLocal(index: Int): Long {
        var result = 0L
        for (k in 0 until 8) {
            result = result or ((this[index + k].toLong() and 0xff) shl (8 * k))
        }
        return result
    }
}

internal class ZipArchive(
    private val source: BinarySource,
    private val index: Map<String, ZipReader.EntryMeta>,
    private val isActive: () -> Boolean,
    private val closeSource: Boolean = false,
) {
    private val cache = linkedMapOf<String, ByteArray>()

    val names: Set<String> get() = index.keys

    fun contains(path: String): Boolean = entryMeta(path) != null

    fun read(path: String): ByteArray? {
        val meta = entryMeta(path) ?: return null
        cache[meta.name]?.let { return it }
        if (!isActive()) throw ParseCancelledException("ZIP parse cancelled")
        val data = ZipReader.inflateEntry(source, meta)
        cache[meta.name] = data
        return data
    }

    fun readString(path: String): String =
        read(path)?.decodeToString().orEmpty()

    /** Inflate, decode, then drop cached bytes to reduce peak RAM. */
    fun readStringAndEvict(path: String): String {
        val data = read(path) ?: return ""
        val text = data.decodeToString()
        evict(path)
        return text
    }

    fun evict(path: String) {
        val meta = entryMeta(path) ?: return
        cache.remove(meta.name)
    }

    fun evictAll() {
        cache.clear()
    }

    fun close() {
        cache.clear()
        if (closeSource) source.close()
    }

    private fun entryMeta(path: String): ZipReader.EntryMeta? {
        index[path]?.let { return it }
        index.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value?.let { return it }
        val leaf = path.substringAfterLast('/')
        return index.entries.firstOrNull {
            it.key.replace('\\', '/').endsWith(leaf, ignoreCase = true) &&
                it.key.contains("xl/", ignoreCase = true)
        }?.value
    }
}

class ParseCancelledException(message: String) : IllegalStateException(message)

class EncryptedWorkbookException(message: String) : IllegalStateException(message)

class UnsupportedZipException(message: String) : IllegalStateException(message)
