package com.aj.shared.parser

/**
 * Minimal custom BIFF8 (.xls) reader — OLE CFB + common cell records.
 * Supports LABEL / LABELSST / NUMBER / RK / MULRK / FORMULA (cached) / BOOLERR.
 * Complex macros/charts/password-encrypted Workbook streams are rejected with a clear error.
 */
internal object XlsParser {

    fun parse(
        bytes: ByteArray,
        options: FileParseOptions,
        sourceName: String?,
    ): ParseResult {
        return try {
            if (bytes.size < 512) {
                return ParseResult.Error("File too small to be a valid .xls", format = FileParseFormat.XLS)
            }
            if (!options.isActive()) {
                return ParseResult.Error("Parse cancelled", format = FileParseFormat.XLS)
            }
            val streams = OleCompoundFile.readStreams(bytes)
            val workbook = streams.entries.firstOrNull { (name, _) ->
                val n = name.trim('\u0000').trim()
                n.equals("Workbook", true) || n.equals("Book", true)
            }?.value
                ?: return ParseResult.Error(
                    "Could not find Workbook stream in .xls (unsupported/corrupt OLE).",
                    format = FileParseFormat.XLS,
                )

            // Encrypted workbook often starts with FILEPASS (0x002F) early
            if (hasFilePass(workbook)) {
                return ParseResult.Error(
                    message = "This .xls is password-protected. Remove the password and retry, or save as .xlsx/.csv.",
                    format = FileParseFormat.XLS,
                    code = "ENCRYPTED",
                )
            }

            val workbookParse = parseWorkbook(workbook, options)
            val sheets = workbookParse.sheets.map { parsed ->
                matrixToSheet(
                    name = parsed.sheetName,
                    matrix = parsed.matrix,
                    hasHeader = options.hasHeader,
                    maxRows = options.maxRows,
                    uniquifyHeaders = options.uniquifyHeaders,
                    includeRowId = options.includeRowId,
                    autoDetectHeader = options.autoDetectHeader,
                )
            }
            ParseResult.Success(
                ParseSuccess(
                    format = FileParseFormat.XLS,
                    sheets = sheets,
                    sourceName = sourceName,
                    warnings = listOf("Legacy .xls via minimal BIFF reader — prefer .xlsx for full fidelity.") +
                        workbookParse.warnings,
                    truncated = workbookParse.truncated,
                ),
            )
        } catch (t: ParseCancelledException) {
            ParseResult.Error(t.message ?: "Parse cancelled", cause = t, format = FileParseFormat.XLS)
        } catch (t: Throwable) {
            ParseResult.Error(
                message = "Failed to parse .xls: ${t.message ?: t::class.simpleName}. " +
                    "If this keeps failing, Save As → .xlsx or .csv.",
                cause = t,
                format = FileParseFormat.XLS,
            )
        }
    }

    private data class BoundSheet(val offset: Int, val name: String)
    private data class BiffResult(
        val sheetName: String,
        val matrix: List<List<String>>,
        val warnings: List<String>,
        val truncated: Boolean,
    )
    private data class WorkbookParse(
        val sheets: List<BiffResult>,
        val warnings: List<String>,
        val truncated: Boolean,
    )

    private fun hasFilePass(data: ByteArray): Boolean {
        var i = 0
        var scanned = 0
        while (i + 4 <= data.size && scanned < 64) {
            val type = data.readU16(i)
            val len = data.readU16(i + 2)
            if (type == 0x002F) return true
            i += 4 + len
            scanned++
        }
        return false
    }

    private fun parseWorkbook(data: ByteArray, options: FileParseOptions): WorkbookParse {
        val sst = mutableListOf<String>()
        val bounds = ArrayList<BoundSheet>()
        val warnings = mutableListOf<String>()
        var i = 0
        // Pass 1: global records (SST + BOUNDSHEET)
        while (i + 4 <= data.size) {
            if (!options.isActive()) throw ParseCancelledException("Parse cancelled")
            val type = data.readU16(i)
            val len = data.readU16(i + 2)
            val start = i + 4
            val end = (start + len).coerceAtMost(data.size)
            val rec = data.copyOfRange(start, end)
            when (type) {
                0x0085 -> { // BOUNDSHEET
                    if (rec.size >= 8) {
                        val offset = rec.readI32(0)
                        val nameLen = rec[6].toInt() and 0xff
                        val compressed = rec[7].toInt() and 0x01 == 0
                        val name = if (nameLen > 0 && rec.size >= 8 + nameLen) {
                            if (compressed) rec.copyOfRange(8, 8 + nameLen).decodeToString()
                            else decodeUtf16Le(rec, 8, nameLen)
                        } else "Sheet${bounds.size + 1}"
                        if (offset >= 0) bounds += BoundSheet(offset, name.ifBlank { "Sheet${bounds.size + 1}" })
                    }
                }
                0x00FC -> parseSst(rec, data, end, sst)
            }
            i = end
            // Stop global scan once we hit first worksheet BOF after bounds collected? keep full scan for SST
        }
        if (sst.isEmpty()) warnings += "SST empty or incomplete — some text cells may be blank."

        val selectedBounds = when {
            options.allSheets || bounds.isEmpty() ->
                bounds.ifEmpty { listOf(BoundSheet(0, "Sheet1")) }
            options.sheetName != null ->
                listOf(
                    bounds.firstOrNull { it.name.equals(options.sheetName, true) }
                        ?: bounds.getOrNull(options.sheetIndex)
                        ?: bounds.first(),
                )
            else -> listOf(bounds.getOrNull(options.sheetIndex) ?: bounds.first())
        }

        var truncated = false
        val sheets = selectedBounds.map { bound ->
            val startAt = if (bound.offset in data.indices) bound.offset else 0
            val parsed = parseSheetStream(data, startAt, bound.name, sst, options)
            if (parsed.truncated) truncated = true
            warnings += parsed.warnings
            parsed
        }
        return WorkbookParse(sheets, warnings.distinct(), truncated)
    }

    private fun parseSheetStream(
        data: ByteArray,
        startOffset: Int,
        sheetName: String,
        sst: List<String>,
        options: FileParseOptions,
    ): BiffResult {
        val grid = mutableMapOf<Int, MutableMap<Int, String>>()
        var maxCol = -1
        var truncated = false
        val warnings = mutableListOf<String>()
        var i = startOffset.coerceIn(0, data.size)
        var cellCount = 0
        var seenBof = startOffset == 0

        while (i + 4 <= data.size) {
            if (!options.isActive()) throw ParseCancelledException("Parse cancelled")
            val type = data.readU16(i)
            val len = data.readU16(i + 2)
            val start = i + 4
            val end = (start + len).coerceAtMost(data.size)
            val rec = data.copyOfRange(start, end)
            when (type) {
                0x0809 -> { // BOF
                    val bofType = if (rec.size >= 4) rec.readU16(2) else 0
                    // 0x0010 = worksheet
                    if (seenBof && bofType == 0x0010 && i > startOffset) break
                    if (bofType == 0x0010) seenBof = true
                }
                0x000A -> break // EOF
                0x0203 -> if (rec.size >= 14) {
                    val row = rec.readU16(0); val col = rec.readU16(2)
                    val bits = rec.readI64(6)
                    val value = Double.fromBits(bits).let {
                        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                    }
                    put(grid, row, col, value, options) { truncated = true }
                    if (col > maxCol) maxCol = col
                    cellCount++
                }
                0x027E -> if (rec.size >= 10) {
                    val row = rec.readU16(0); val col = rec.readU16(2)
                    put(grid, row, col, decodeRk(rec.readI32(6)), options) { truncated = true }
                    if (col > maxCol) maxCol = col
                    cellCount++
                }
                0x0204 -> if (rec.size >= 8) {
                    val row = rec.readU16(0); val col = rec.readU16(2)
                    val n = rec.readU16(6)
                    val text = if (rec.size >= 8 + n) rec.copyOfRange(8, 8 + n).decodeToString() else ""
                    put(grid, row, col, text, options) { truncated = true }
                    if (col > maxCol) maxCol = col
                    cellCount++
                }
                0x00FD -> if (rec.size >= 10) {
                    val row = rec.readU16(0); val col = rec.readU16(2)
                    val idx = rec.readI32(6)
                    put(grid, row, col, sst.getOrNull(idx).orEmpty(), options) { truncated = true }
                    if (col > maxCol) maxCol = col
                    cellCount++
                }
                0x0205 -> if (rec.size >= 8) {
                    val row = rec.readU16(0); val col = rec.readU16(2)
                    val v = rec[6].toInt() and 0xff
                    val isErr = rec[7].toInt() and 0xff != 0
                    val text = if (isErr) "#ERR$v" else if (v != 0) "TRUE" else "FALSE"
                    put(grid, row, col, text, options) { truncated = true }
                    if (col > maxCol) maxCol = col
                    cellCount++
                }
                0x0006 -> if (rec.size >= 14) {
                    // FORMULA — use cached result (IEEE double / string / bool / err)
                    val row = rec.readU16(0); val col = rec.readU16(2)
                    val cached = decodeFormulaCached(rec)
                    if (cached != null) {
                        put(grid, row, col, cached, options) { truncated = true }
                        if (col > maxCol) maxCol = col
                        cellCount++
                    }
                }
                0x00BD -> if (rec.size >= 6) {
                    // MULRK — row, colFirst, then RK+xf pairs, colLast
                    val row = rec.readU16(0)
                    var col = rec.readU16(2)
                    var p = 4
                    while (p + 6 <= rec.size - 2) {
                        // xf (2) + rk (4)
                        val rk = rec.readI32(p + 2)
                        put(grid, row, col, decodeRk(rk), options) { truncated = true }
                        if (col > maxCol) maxCol = col
                        cellCount++
                        col++
                        p += 6
                    }
                }
                0x00BE -> if (rec.size >= 6) {
                    // MULBLANK — mark span consumed (empty cells); nothing to store
                }
            }
            if (cellCount > options.maxCells) {
                truncated = true
                warnings += "Truncated at maxCells=${options.maxCells}"
                break
            }
            i = end
        }

        if (grid.isEmpty()) {
            warnings += "No cell records found on \"$sheetName\" — unsupported BIFF features possible."
        }
        SimpleFormulaEvaluator.evaluateGrid(grid)
        val width = (maxCol + 1).coerceAtMost(options.maxColumns).coerceAtLeast(0)
        val matrix = ArrayList<List<String>>()
        for (r in (grid.keys.minOrNull() ?: 0)..(grid.keys.maxOrNull() ?: -1)) {
            val rowMap = grid[r] ?: continue
            val line = MutableList(width) { "" }
            for ((c, v) in rowMap) if (c in 0 until width) line[c] = v
            matrix += line
            if (options.maxRows != null && matrix.size > options.maxRows + 1) {
                truncated = true
                break
            }
        }
        return BiffResult(sheetName, matrix, warnings, truncated)
    }

    private fun put(
        grid: MutableMap<Int, MutableMap<Int, String>>,
        row: Int,
        col: Int,
        value: String,
        options: FileParseOptions,
        onTruncate: () -> Unit,
    ) {
        if (col >= options.maxColumns) {
            onTruncate(); return
        }
        if (options.maxRows != null && row >= options.maxRows + 1) {
            onTruncate(); return
        }
        grid.getOrPut(row) { mutableMapOf() }[col] = value
    }

    private fun parseSst(rec: ByteArray, full: ByteArray, nextOffset: Int, sst: MutableList<String>) {
        if (rec.size < 8) return
        val total = rec.readI32(0)
        // Unique count at +4 — we just read strings sequentially from offset 8
        var p = 8
        var guard = 0
        while (sst.size < total && p < rec.size && guard < total + 10) {
            guard++
            if (p >= rec.size) break
            val charCount = rec.readU16(p); p += 2
            if (p >= rec.size) break
            val flags = rec[p].toInt() and 0xff; p += 1
            val compressed = flags and 0x01 == 0
            val hasRich = flags and 0x08 != 0
            val hasHist = flags and 0x04 != 0
            if (hasRich && p + 2 <= rec.size) p += 2
            if (hasHist && p + 4 <= rec.size) p += 4
            val byteLen = if (compressed) charCount else charCount * 2
            if (p + byteLen > rec.size) break
            val text = if (compressed) {
                rec.copyOfRange(p, p + byteLen).decodeToString()
            } else {
                decodeUtf16Le(rec, p, charCount)
            }
            sst += text
            p += byteLen
        }
        // Append CONTINUE (0x003C) payloads and keep reading strings
        if (sst.size < total) {
            var off = nextOffset
            val buf = ArrayList<Byte>()
            while (sst.size < total && off + 4 <= full.size) {
                val t = full.readU16(off)
                val l = full.readU16(off + 2)
                if (t != 0x003C) break
                val cont = full.copyOfRange(off + 4, (off + 4 + l).coerceAtMost(full.size))
                off += 4 + l
                // First byte of CONTINUE may be option flags when continuing a string — strip if needed
                var cStart = 0
                if (cont.isNotEmpty() && cont[0].toInt() and 0xff <= 1 && sst.size < total) {
                    // often a flags byte for the continued string; ignore and treat rest as latin1/utf16 remainder — best effort: append as latin1 chunk into next string parse via synthetic record
                    cStart = 0
                }
                for (b in cStart until cont.size) buf += cont[b]
                // Try parse additional complete strings from buffer
                var bp = 0
                val arr = buf.toByteArray()
                while (sst.size < total && bp + 3 <= arr.size) {
                    val charCount = arr.readU16(bp)
                    val flags = arr[bp + 2].toInt() and 0xff
                    val compressed = flags and 0x01 == 0
                    var hp = bp + 3
                    if (flags and 0x08 != 0) {
                        if (hp + 2 > arr.size) break
                        hp += 2
                    }
                    if (flags and 0x04 != 0) {
                        if (hp + 4 > arr.size) break
                        hp += 4
                    }
                    val byteLen = if (compressed) charCount else charCount * 2
                    if (hp + byteLen > arr.size) break
                    val text = if (compressed) arr.copyOfRange(hp, hp + byteLen).decodeToString()
                    else decodeUtf16Le(arr, hp, charCount)
                    sst += text
                    bp = hp + byteLen
                }
                if (bp > 0) {
                    buf.subList(0, bp).clear()
                }
            }
        }
    }

    /** BIFF FORMULA cached value at offset 6 (8 bytes) + flags. */
    private fun decodeFormulaCached(rec: ByteArray): String? {
        if (rec.size < 14) return null
        // If byte 6 == 0x00 and byte 12 == 0xFF and byte 13 == 0xFF → string/bool/err special
        val b6 = rec[6].toInt() and 0xff
        val b12 = rec[12].toInt() and 0xff
        val b13 = rec[13].toInt() and 0xff
        if (b12 == 0xFF && b13 == 0xFF) {
            return when (b6) {
                0x00 -> null // string follows in STRING record — leave blank; rare
                0x01 -> if ((rec[8].toInt() and 0xff) != 0) "TRUE" else "FALSE"
                0x02 -> {
                    val err = rec[8].toInt() and 0xff
                    when (err) {
                        0x00 -> "#NULL!"
                        0x07 -> "#DIV/0!"
                        0x0F -> "#VALUE!"
                        0x17 -> "#REF!"
                        0x1D -> "#NAME?"
                        0x24 -> "#NUM!"
                        0x2A -> "#N/A"
                        else -> "#ERR$err"
                    }
                }
                0x03 -> "" // empty
                else -> null
            }
        }
        val bits = rec.readI64(6)
        val d = Double.fromBits(bits)
        if (d.isNaN()) return null
        return if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
    }

    private fun decodeRk(rk: Int): String {
        val div100 = rk and 0x1 != 0
        val isInt = rk and 0x2 != 0
        val value = if (isInt) {
            (rk shr 2).toDouble()
        } else {
            Double.fromBits((rk and -4).toLong() shl 32)
        }.let { if (div100) it / 100.0 else it }
        return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }

    private fun decodeUtf16Le(bytes: ByteArray, offset: Int, chars: Int): String {
        val out = StringBuilder(chars)
        var i = offset
        repeat(chars) {
            if (i + 1 >= bytes.size) return@repeat
            val c = (bytes[i].toInt() and 0xff) or ((bytes[i + 1].toInt() and 0xff) shl 8)
            out.append(c.toChar())
            i += 2
        }
        return out.toString()
    }

    private fun ByteArray.readU16(i: Int): Int =
        (this[i].toInt() and 0xff) or ((this[i + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.readI32(i: Int): Int =
        (this[i].toInt() and 0xff) or
            ((this[i + 1].toInt() and 0xff) shl 8) or
            ((this[i + 2].toInt() and 0xff) shl 16) or
            ((this[i + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.readI64(i: Int): Long {
        var v = 0L
        for (k in 0 until 8) v = v or ((this[i + k].toLong() and 0xff) shl (8 * k))
        return v
    }
}

/** Minimal OLE Compound File reader — extracts named streams. */
internal object OleCompoundFile {
    fun readStreams(bytes: ByteArray): Map<String, ByteArray> {
        require(bytes.size >= 512) { "OLE too small" }
        require(
            bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
                bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte(),
        ) { "Not an OLE compound document" }

        val sectorSize = 1 shl bytes.readU16(0x1E).let { if (it in 6..15) it else 9 }
        val miniSectorSize = 1 shl bytes.readU16(0x20).let { if (it in 2..15) it else 6 }
        val fatSectors = bytes.readI32(0x2C)
        val dirStart = bytes.readI32(0x30)
        val miniCutoff = bytes.readI32(0x38).let { if (it > 0) it else 4096 }
        val miniFatStart = bytes.readI32(0x3C)
        val difatStart = bytes.readI32(0x44)

        val fat = ArrayList<Int>()
        // First 109 DIFAT entries in header
        for (i in 0 until 109) {
            val sec = bytes.readI32(0x4C + i * 4)
            if (sec >= 0) fat += readSectorInts(bytes, sec, sectorSize)
        }
        var dif = difatStart
        var guard = 0
        while (dif >= 0 && guard++ < 1024) {
            val sector = readSector(bytes, dif, sectorSize)
            val count = sectorSize / 4
            for (i in 0 until count - 1) {
                val sec = sector.readI32(i * 4)
                if (sec >= 0) fat += readSectorInts(bytes, sec, sectorSize)
            }
            dif = sector.readI32((count - 1) * 4)
        }
        // Flatten fat chain references — fat list above is concatenation of FAT sectors' entries
        val fatTable = fat // already chain of next-sector ints

        fun streamSectors(start: Int, size: Int, useMini: Boolean): ByteArray {
            val out = ArrayList<Byte>()
            var sec = start
            var left = size
            var hops = 0
            while (sec >= 0 && left > 0 && hops++ < 1_000_000) {
                val chunk = if (useMini) {
                    // Mini stream lives inside Root Storage stream — resolve via root
                    readMiniSector(bytes, sec, sectorSize, miniSectorSize, fatTable, dirStart)
                } else {
                    readSector(bytes, sec, sectorSize)
                }
                val take = minOf(left, chunk.size)
                for (b in 0 until take) out += chunk[b]
                left -= take
                sec = fatTable.getOrNull(sec) ?: -1
                if (sec >= 0xFFFFFFFA.toInt()) break // END/FREE markers
            }
            return out.toByteArray()
        }

        // Directory entries are 128 bytes each in a stream starting at dirStart
        val dirBytes = readChain(bytes, dirStart, sectorSize, fatTable)
        val root = if (dirBytes.size >= 128) parseDirEntry(dirBytes, 0) else null
        val miniStream = if (root != null && root.startSector >= 0 && root.size > 0) {
            readChain(bytes, root.startSector, sectorSize, fatTable).copyOf(root.size.coerceAtMost(dirBytes.size * 4 + root.size))
        } else {
            ByteArray(0)
        }

        val streams = linkedMapOf<String, ByteArray>()
        var idx = 0
        while (idx + 128 <= dirBytes.size) {
            val e = parseDirEntry(dirBytes, idx)
            idx += 128
            if (e.type != 2) continue // stream
            if (e.name.isBlank()) continue
            val data = if (e.size < miniCutoff && miniStream.isNotEmpty()) {
                readMiniChain(miniStream, e.startSector, e.size, miniSectorSize, bytes, miniFatStart, sectorSize, fatTable)
            } else {
                readChain(bytes, e.startSector, sectorSize, fatTable).let {
                    if (e.size in 1 until it.size) it.copyOf(e.size) else it
                }
            }
            streams[e.name] = data
        }
        return streams
    }

    private data class DirEntry(
        val name: String,
        val type: Int,
        val startSector: Int,
        val size: Int,
    )

    private fun parseDirEntry(dir: ByteArray, offset: Int): DirEntry {
        val nameBytes = dir.copyOfRange(offset, offset + 64)
        val nameLen = dir.readU16(offset + 64).coerceIn(0, 64)
        val name = if (nameLen >= 2) {
            decodeUtf16Le(nameBytes, 0, (nameLen / 2) - 1)
        } else ""
        val type = dir[offset + 66].toInt() and 0xff
        val start = dir.readI32(offset + 116)
        val size = dir.readI32(offset + 120)
        return DirEntry(name, type, start, size)
    }

    private fun readSector(bytes: ByteArray, sector: Int, sectorSize: Int): ByteArray {
        val offset = 512 + sector * sectorSize
        require(offset + sectorSize <= bytes.size) { "OLE sector out of range" }
        return bytes.copyOfRange(offset, offset + sectorSize)
    }

    private fun readSectorInts(bytes: ByteArray, sector: Int, sectorSize: Int): List<Int> {
        val sec = readSector(bytes, sector, sectorSize)
        val out = ArrayList<Int>(sectorSize / 4)
        for (i in 0 until sectorSize / 4) out += sec.readI32(i * 4)
        return out
    }

    private fun readChain(bytes: ByteArray, start: Int, sectorSize: Int, fat: List<Int>): ByteArray {
        val out = ArrayList<Byte>()
        var sec = start
        var hops = 0
        while (sec >= 0 && hops++ < 1_000_000) {
            if (sec >= 0xFFFFFFFA.toInt()) break
            val chunk = readSector(bytes, sec, sectorSize)
            for (b in chunk) out += b
            sec = fat.getOrNull(sec) ?: break
        }
        return out.toByteArray()
    }

    private fun readMiniSector(
        bytes: ByteArray,
        miniSec: Int,
        sectorSize: Int,
        miniSectorSize: Int,
        fat: List<Int>,
        dirStart: Int,
    ): ByteArray {
        // Unused in simplified path — mini chain helper below
        return ByteArray(0)
    }

    private fun readMiniChain(
        miniStream: ByteArray,
        start: Int,
        size: Int,
        miniSectorSize: Int,
        bytes: ByteArray,
        miniFatStart: Int,
        sectorSize: Int,
        fat: List<Int>,
    ): ByteArray {
        val miniFat = if (miniFatStart >= 0) readChain(bytes, miniFatStart, sectorSize, fat) else ByteArray(0)
        fun next(sec: Int): Int {
            val off = sec * 4
            return if (off + 4 <= miniFat.size) miniFat.readI32(off) else -1
        }
        val out = ArrayList<Byte>()
        var sec = start
        var left = size
        var hops = 0
        while (sec >= 0 && left > 0 && hops++ < 1_000_000) {
            if (sec >= 0xFFFFFFFA.toInt()) break
            val off = sec * miniSectorSize
            if (off >= miniStream.size) break
            val take = minOf(left, miniSectorSize, miniStream.size - off)
            for (i in 0 until take) out += miniStream[off + i]
            left -= take
            sec = next(sec)
        }
        return out.toByteArray()
    }

    private fun decodeUtf16Le(bytes: ByteArray, offset: Int, chars: Int): String {
        val out = StringBuilder(chars)
        var i = offset
        repeat(chars) {
            if (i + 1 >= bytes.size) return@repeat
            val c = (bytes[i].toInt() and 0xff) or ((bytes[i + 1].toInt() and 0xff) shl 8)
            if (c != 0) out.append(c.toChar())
            i += 2
        }
        return out.toString()
    }

    private fun ByteArray.readU16(i: Int): Int =
        (this[i].toInt() and 0xff) or ((this[i + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.readI32(i: Int): Int =
        (this[i].toInt() and 0xff) or
            ((this[i + 1].toInt() and 0xff) shl 8) or
            ((this[i + 2].toInt() and 0xff) shl 16) or
            ((this[i + 3].toInt() and 0xff) shl 24)
}
