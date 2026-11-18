package com.aj.shared.parser

/**
 * Formats Excel cached cell values (numbers / dates / %) using styles.xml hints.
 */
internal object ExcelValueFormatter {

    data class StyleInfo(
        val numFmtByXf: List<Int>,
        val customFormats: Map<Int, String>,
    )

    fun parseStyles(stylesXml: String): StyleInfo {
        if (stylesXml.isBlank()) return StyleInfo(emptyList(), emptyMap())
        val custom = linkedMapOf<Int, String>()
        SimpleXml.forEachElement(stylesXml, "numFmt") { open, _ ->
            val id = SimpleXml.attr(open, "numFmtId")?.toIntOrNull() ?: return@forEachElement
            val code = SimpleXml.attr(open, "formatCode").orEmpty()
            custom[id] = code
        }
        val xfs = mutableListOf<Int>()
        val cellXfsInner = extractBlock(stylesXml, "cellXfs")
        SimpleXml.forEachElement(cellXfsInner ?: stylesXml, "xf") { open, _ ->
            xfs += SimpleXml.attr(open, "numFmtId")?.toIntOrNull() ?: 0
        }
        return StyleInfo(xfs, custom)
    }

    fun format(
        raw: String,
        type: String?,
        styleIndex: Int?,
        styles: StyleInfo,
        enabled: Boolean,
        date1904: Boolean = false,
    ): String {
        if (!enabled || raw.isBlank()) return raw
        if (type == "s" || type == "inlineStr" || type == "str" || type == "b" || type == "e") {
            return raw
        }
        val num = raw.toDoubleOrNull() ?: return raw
        val fmtId = styleIndex?.let { styles.numFmtByXf.getOrNull(it) } ?: 0
        val code = normalizeFormatCode(styles.customFormats[fmtId] ?: builtinFormat(fmtId))
        return when {
            isDateFormat(fmtId, code) -> formatExcelDate(num, code, date1904)
            fmtId == 9 || fmtId == 10 || code.contains('%') -> formatPercent(num, code)
            isCurrencyFormat(code) -> formatCurrency(num, code)
            isAccountingLike(code) -> formatAccounting(num, code)
            isScientific(code) -> formatScientific(num)
            num == num.toLong().toDouble() && !code.contains('.') && !code.contains('e', true) ->
                num.toLong().toString()
            else -> trimNumber(raw)
        }
    }

    private fun isScientific(code: String): Boolean {
        if (code.isBlank()) return false
        val c = code.lowercase()
        return c.contains("e+") || c.contains("0e-") || c.contains("scientific")
    }

    private fun formatScientific(num: Double): String {
        // Compact portable scientific without locale deps.
        if (num == 0.0) return "0"
        val sign = if (num < 0) "-" else ""
        val abs = kotlin.math.abs(num)
        var exp = 0
        var m = abs
        while (m >= 10.0) {
            m /= 10.0; exp++
        }
        while (m < 1.0 && m > 0.0) {
            m *= 10.0; exp--
        }
        val mantissa = (kotlin.math.round(m * 10000.0) / 10000.0)
        return "$sign${trimNumber(mantissa.toString())}E${if (exp >= 0) "+" else ""}$exp"
    }

    /** Strip Excel accounting wrappers `_(`, `_)`, `* `, quoted literals noise. */
    private fun normalizeFormatCode(code: String): String {
        if (code.isBlank()) return code
        return code
            .replace("_(", "")
            .replace("_)", "")
            .replace("_-", "")
            .replace(Regex("""\* +"""), "")
            .replace(Regex("""\\."""), "")
            .trim()
    }

    private fun isAccountingLike(code: String): Boolean {
        if (code.isBlank()) return false
        return code.contains("#,##0")
    }

    private fun formatAccounting(num: Double, code: String): String {
        val rounded = if (code.contains(".00")) {
            (kotlin.math.round(num * 100.0) / 100.0)
        } else if (code.contains(".0") && !code.contains(".00")) {
            (kotlin.math.round(num * 10.0) / 10.0)
        } else {
            num
        }
        val body = if (code.contains(".00")) {
            val cents = kotlin.math.round(rounded * 100.0).toLong()
            val whole = cents / 100
            val frac = kotlin.math.abs(cents % 100).toString().padStart(2, '0')
            "${insertThousands(whole.toString())}.$frac"
        } else if (rounded == rounded.toLong().toDouble()) {
            insertThousands(rounded.toLong().toString())
        } else {
            insertThousands(trimNumber(rounded.toString()))
        }
        return if (isCurrencyFormat(code)) {
            val symbol = when {
                code.contains('₹') || code.contains("INR", true) -> "₹"
                code.contains('€') || code.contains("EUR", true) -> "€"
                code.contains('£') || code.contains("GBP", true) -> "£"
                code.contains('¥') -> "¥"
                code.contains('₩') -> "₩"
                code.contains("R$") -> "R$"
                code.contains("CHF", true) -> "CHF "
                else -> "\$"
            }
            "$symbol$body"
        } else body
    }

    private fun insertThousands(raw: String): String {
        val neg = raw.startsWith('-')
        val s = if (neg) raw.drop(1) else raw
        val parts = s.split('.', limit = 2)
        val grouped = parts[0].reversed().chunked(3).joinToString(",").reversed()
        val out = if (parts.size > 1) "$grouped.${parts[1]}" else grouped
        return if (neg) "-$out" else out
    }

    private fun extractBlock(xml: String, tag: String): String? {
        var found: String? = null
        SimpleXml.forEachElement(xml, tag) { _, inner ->
            if (found == null) found = inner
        }
        return found
    }

    private fun builtinFormat(id: Int): String = when (id) {
        5, 6, 7, 8 -> "\$#,##0"
        14 -> "mm-dd-yy"
        15 -> "d-mmm-yy"
        16 -> "d-mmm"
        17 -> "mmm-yy"
        18 -> "h:mm AM/PM"
        19 -> "h:mm:ss AM/PM"
        20 -> "h:mm"
        21 -> "h:mm:ss"
        22 -> "m/d/yy h:mm"
        37, 38, 39, 40 -> "#,##0"
        45 -> "mm:ss"
        46 -> "[h]:mm:ss"
        47 -> "mmss.0"
        else -> ""
    }

    private fun isDateFormat(fmtId: Int, code: String): Boolean {
        if (fmtId in 14..22 || fmtId in 45..47) return true
        if (code.isBlank()) return false
        val stripped = code.lowercase()
            .replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("\\[[^]]*]"), "")
            .replace(Regex("\\$.*"), "")
        if (stripped.all { it in "0#?,. %e+- " }) return false
        return stripped.any { it in "ymdhs" }
    }

    private fun isCurrencyFormat(code: String): Boolean {
        if (code.isBlank()) return false
        return code.contains('$') || code.contains('€') || code.contains('£') ||
            code.contains('¥') || code.contains('₹') || code.contains('₩') ||
            code.contains('₫') || code.contains("R$") || code.contains("CHF") ||
            code.contains("kr") || code.contains("[\$") ||
            code.contains("USD", true) || code.contains("EUR", true) ||
            code.contains("GBP", true) || code.contains("INR", true)
    }

    private fun formatCurrency(num: Double, code: String): String {
        val symbol = when {
            code.contains('₹') || code.contains("\$₹") || code.contains("INR", true) -> "₹"
            code.contains('€') || code.contains("EUR", true) -> "€"
            code.contains('£') || code.contains("GBP", true) -> "£"
            code.contains('¥') || code.contains("JPY", true) || code.contains("CNY", true) -> "¥"
            code.contains('₩') || code.contains("KRW", true) -> "₩"
            code.contains('₫') || code.contains("VND", true) -> "₫"
            code.contains("R$") || code.contains("BRL", true) -> "R$"
            code.contains("CHF", true) -> "CHF "
            code.contains("kr", true) || code.contains("SEK", true) || code.contains("NOK", true) -> "kr"
            else -> "\$"
        }
        val body = if (num == num.toLong().toDouble()) {
            insertThousands(num.toLong().toString())
        } else {
            insertThousands(trimNumber(num.toString()))
        }
        return "$symbol$body"
    }

    private fun formatExcelDate(serial: Double, code: String, date1904: Boolean): String {
        val epochDay = serial.toLong()
        val fraction = serial - epochDay
        // 1900 system: Excel day 25569 = 1970-01-01; 1904 system: 24107 = 1970-01-01
        val unixDays = if (date1904) epochDay - 24107 else epochDay - 25569
        val millis = unixDays * 86_400_000L + (fraction * 86_400_000.0).toLong()
        val totalSeconds = millis / 1000
        val daysFromEpoch = floorDiv(totalSeconds, 86_400)
        var rem = floorMod(totalSeconds, 86_400).toInt()
        val hour = rem / 3600
        rem %= 3600
        val minute = rem / 60
        val second = rem % 60
        val (y, m, d) = civilFromDays(daysFromEpoch.toInt())
        val lower = code.lowercase()
            .replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("\\[[^]]*]"), "")
        val wantsTime = lower.contains('h') || (lower.contains('s') && lower.contains(':')) ||
            lower.contains("am/pm") || lower.contains("a/p")
        val wantsDate = lower.contains('y') || lower.contains('d') ||
            (lower.contains('m') && !lower.contains("am/pm") && !lower.contains("mm:ss"))
        val iso = "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
        val dmy = "${d.toString().padStart(2, '0')}/${m.toString().padStart(2, '0')}/${y.toString().padStart(4, '0')}"
        val mdy = "${m.toString().padStart(2, '0')}/${d.toString().padStart(2, '0')}/${y.toString().takeLast(2)}"
        // Prefer locale-ish order from format tokens (d before m → DMY; m before d → MDY).
        val date = when {
            Regex("""d+[^ym]*m+[^yd]*y+""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> dmy
            Regex("""m+[^yd]*d+[^ym]*y+""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> mdy
            else -> iso
        }
        val time = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:${second.toString().padStart(2, '0')}"
        return when {
            wantsDate && wantsTime -> "$date $time"
            wantsTime && !wantsDate -> time
            else -> date
        }
    }

    private fun formatPercent(num: Double, code: String): String {
        val pct = num * 100.0
        return if (code.contains("0.00") || code.contains("0.0")) {
            "${trimNumber(pct.toString())}%"
        } else {
            "${pct.toLong()}%"
        }
    }

    private fun trimNumber(raw: String): String {
        if (!raw.contains('.')) return raw
        return raw.trimEnd('0').trimEnd('.')
    }

    private fun floorDiv(a: Long, b: Long): Long {
        var q = a / b
        val r = a % b
        if ((r != 0L) && ((a < 0) != (b < 0))) q--
        return q
    }

    private fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b

    private fun civilFromDays(z: Int): Triple<Int, Int, Int> {
        val z2 = z + 719468
        val era = (if (z2 >= 0) z2 else z2 - 146096) / 146097
        val doe = z2 - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = mp + (if (mp < 10) 3 else -9)
        val year = y + (if (m <= 2) 1 else 0)
        return Triple(year, m, d)
    }
}
