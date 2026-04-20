package com.aj.shared.extension

import io.ktor.util.date.getTimeMillis
import kotlinx.datetime.*
import kotlinx.datetime.format.*
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlin.jvm.JvmOverloads

// --- Extensions ---

fun Any?.toDdMmmYyyy(format: String = "dd MMM yyyy") = formatDateCMP(this, format)

fun Any?.toYyyyMmDd(format: String = "yyyy/MM/dd") = formatDateCMP(this, format)

fun Any?.toServerDate(format: String = "yyyy-MM-dd") = formatDateCMP(this, format)

fun Any?.toDateTimeSeconds(): String = formatDateCMP(this, "dd MMM yyyy hh:mm:ss a")

fun Any?.toDateTime(): String = formatDateCMP(this, "dd MMM yyyy hh:mm a")

// --- Core Formatter Function ---

@OptIn(FormatStringsInDatetimeFormats::class)
fun formatDateCMP(
    input: Any?,
    outputFormat: String = "dd MMM yyyy",
): String {
    if (input == null) return ""

    // Output formatter setup
    val outputFormatter = try {
        LocalDateTime.Format { byUnicodePattern(outputFormat) }
    } catch (e: Exception) {
        // Fallback agar pattern invalid ho
        LocalDateTime.Format { byUnicodePattern("dd-MM-yyyy") }
    }

    val tz = TimeZone.currentSystemDefault()

    try {
        // 1. Agar Long (Timestamp) hai
        if (input is Long) {
            val dateTime = Instant.fromEpochMilliseconds(input).toLocalDateTime(tz)
            return dateTime.format(outputFormatter)
        }

        val text = input.toString().trim()
        if (text.isEmpty()) return ""

        // 2. Try parsing from possible string formats
        val possibleFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "yyyy/MM/dd",
            "yyyyMMdd"
        )

        for (pattern in possibleFormats) {
            try {
                // Try as LocalDateTime
                val formatter = LocalDateTime.Format { byUnicodePattern(pattern) }
                val parsed = LocalDateTime.parse(text, formatter)
                return parsed.format(outputFormatter)
            } catch (_: Exception) {
                try {
                    // Try as LocalDate (if time is missing)
                    val dateLine = if (text.contains("T")) text.split("T")[0] else text
                    val formatter = LocalDate.Format { byUnicodePattern(pattern) }
                    val parsedDate = LocalDate.parse(dateLine, formatter)

                    // Format output using LocalDate formatter to avoid LocalDateTime crash
                    val outDateFormatter = LocalDate.Format { byUnicodePattern(outputFormat.split(" ")[0]) }
                    return parsedDate.format(outDateFormatter)
                } catch (_: Exception) { }
            }
        }

        // 3. Agar String ke andar Milliseconds chhupe ho "1713598000000"
        text.toLongOrNull()?.let { millis ->
            return Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).format(outputFormatter)
        }

        return text
    } catch (e: Exception) {
        return input.toString()
    }
}

// --- Object Utils ---

@OptIn(FormatStringsInDatetimeFormats::class)
object DateUtils {

    private fun now(): LocalDateTime {
        val instant = Instant.fromEpochMilliseconds(
            getTimeMillis()
        )
        return instant.toLocalDateTime(TimeZone.currentSystemDefault())
    }

    fun currentDate(format: String = "dd MMM yyyy"): String {
        val formatter = LocalDateTime.Format { byUnicodePattern(format) }
        return now().format(formatter)
    }

    fun currentDateTime(format: String = "dd MMM yyyy hh:mm a"): String {
        val formatter = LocalDateTime.Format { byUnicodePattern(format) }
        return now().format(formatter)
    }

    fun currentYear(): String = now().year.toString()

    fun currentMonthNumber(): String = now().monthNumber.toString().padStart(2, '0')

    fun currentMonthName(): String = now().month.name.lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    fun currentDay(): String = now().dayOfMonth.toString().padStart(2, '0')
}

// --- Global Helper Functions ---

fun currentDate() = DateUtils.currentDate()
fun currentDateTime() = DateUtils.currentDateTime()
fun currentYear() = DateUtils.currentYear()
fun currentMonthNumber() = DateUtils.currentMonthNumber()
fun currentMonthName() = DateUtils.currentMonthName()
fun currentDay() = DateUtils.currentDay()