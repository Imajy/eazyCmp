package com.aj.shared.parser

internal object ExcelParser {
    fun parse(
        bytes: ByteArray,
        format: FileParseFormat,
        options: FileParseOptions,
        sourceName: String?,
    ): ParseResult {
        if (looksLikeOle(bytes)) {
            // Password-protected .xlsx is OLE + EncryptedPackage (not BIFF).
            if (OoxmlAgileDecrypt.isEncryptedOle(bytes)) {
                val pw = options.password
                if (pw.isNullOrEmpty()) {
                    return ParseResult.Error(
                        message = "This Excel file is password-protected. " +
                            "Pass FileParseOptions(password = \"…\") or remove encryption in Excel.",
                        format = FileParseFormat.XLSX,
                        code = "ENCRYPTED",
                    )
                }
                return try {
                    val zipBytes = OoxmlAgileDecrypt.tryDecrypt(bytes, pw)
                        ?: return ParseResult.Error(
                            message = "Could not decrypt workbook (unsupported scheme or corrupt EncryptionInfo).",
                            format = FileParseFormat.XLSX,
                            code = "ENCRYPTED",
                        )
                    XlsxParser.parse(zipBytes, options, sourceName)
                } catch (t: EncryptedWorkbookException) {
                    ParseResult.Error(t.message ?: "Encrypted workbook", cause = t, format = FileParseFormat.XLSX, code = "ENCRYPTED")
                } catch (t: Throwable) {
                    ParseResult.Error(
                        message = "Decrypt failed: ${t.message ?: t::class.simpleName}",
                        cause = t,
                        format = FileParseFormat.XLSX,
                        code = "ENCRYPTED",
                    )
                }
            }
            return XlsParser.parse(bytes, options, sourceName)
        }
        return when (format) {
            FileParseFormat.XLSX, FileParseFormat.XLS ->
                XlsxParser.parse(bytes, options, sourceName)
            else -> ParseResult.Error("Not an Excel format: $format", format = format)
        }
    }

    private fun looksLikeOle(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
}
