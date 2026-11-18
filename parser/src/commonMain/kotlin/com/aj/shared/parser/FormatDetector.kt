package com.aj.shared.parser

internal object FormatDetector {

    fun detect(bytes: ByteArray, fileName: String?, mimeType: String?): FileParseFormat {
        byExtension(fileName)?.let { return it }
        byMime(mimeType)?.let { return it }
        return bySniff(bytes)
    }

    private fun byExtension(fileName: String?): FileParseFormat? {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()?.trim().orEmpty()
        if (ext.isEmpty()) return null
        return when (ext) {
            "csv" -> FileParseFormat.CSV
            "tsv", "tab" -> FileParseFormat.TSV
            "xlsx" -> FileParseFormat.XLSX
            "xls" -> FileParseFormat.XLS
            "vcf", "vcard" -> FileParseFormat.VCARD
            else -> null
        }
    }

    private fun byMime(mimeType: String?): FileParseFormat? {
        val mime = mimeType?.lowercase()?.trim().orEmpty()
        if (mime.isEmpty()) return null
        return when {
            mime.contains("csv") -> FileParseFormat.CSV
            mime.contains("tab-separated") || mime.contains("tsv") -> FileParseFormat.TSV
            mime.contains("spreadsheetml") || mime.contains("xlsx") -> FileParseFormat.XLSX
            mime == "application/vnd.ms-excel" || mime.endsWith("ms-excel") -> FileParseFormat.XLS
            mime.contains("vcard") -> FileParseFormat.VCARD
            else -> null
        }
    }

    private fun bySniff(bytes: ByteArray): FileParseFormat {
        if (bytes.isEmpty()) return FileParseFormat.UNKNOWN
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        ) {
            return FileParseFormat.XLSX
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
        ) {
            return FileParseFormat.XLS
        }
        val sample = bytes.decodeToString(endIndex = minOf(bytes.size, 4096))
        val trimmed = sample.trimStart()
        if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) return FileParseFormat.VCARD
        val firstLine = trimmed.lineSequence().firstOrNull().orEmpty()
        val tabs = firstLine.count { it == '\t' }
        val commas = firstLine.count { it == ',' }
        return when {
            tabs > commas && tabs > 0 -> FileParseFormat.TSV
            commas > 0 || firstLine.contains(';') -> FileParseFormat.CSV
            else -> FileParseFormat.UNKNOWN
        }
    }
}
