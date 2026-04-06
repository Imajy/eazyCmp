package com.aj.shared.extension

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

fun Any?.toDdMmmYyyy(
    format: String = "dd MMM yyyy"
) = formatDateCMP(this, format)

fun Any?.toYyyyMmDd(
    format: String = "yyyy/MM/dd"
) = formatDateCMP(this, format)

fun Any?.toServerDate(
    format: String = "yyyy-MM-dd"
) = formatDateCMP(this, format)


fun Any?.toDateTimeSeconds(): String =
    formatDateCMP(
        input = this,
        outputFormat = "dd MMM yyyy hh:mm:ss a"
    )

fun Any?.toDateTime(): String =
    formatDateCMP(
        input = this,
        outputFormat = "dd MMM yyyy hh:mm a"
    )


@OptIn(FormatStringsInDatetimeFormats::class)
fun formatDateCMP(
    input: Any?,
    outputFormat: String = "dd MMM yyyy",
): String {
    if (input == null) return ""
    val outputFormatter = LocalDateTime.Format { byUnicodePattern(outputFormat) }

    try {
        if (input is Long) {
            val dateTime = kotlin.time.Instant.fromEpochMilliseconds(input).toLocalDateTime(TimeZone.currentSystemDefault())
            return dateTime.format(outputFormatter)
        }

        val text = input.toString()

        val possibleFormats = listOf(
            "yyyy-MM-dd",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "yyyy/MM/dd",
            "yyyyMMdd"
        )
        possibleFormats.forEach { pattern ->
            try {
                val formatter = LocalDateTime.Format { byUnicodePattern(pattern) }
                val parsed = LocalDateTime.parse(text, formatter)
                return parsed.format(outputFormatter)
            } catch (_: Exception) {
                try {
                    val parsedDate = LocalDate.parse(text, LocalDate.Format { byUnicodePattern(pattern) })

                    return parsedDate.format(LocalDate.Format { byUnicodePattern(outputFormat) })
                } catch (_: Exception) { }
            }
        }

        text.toLongOrNull()?.let { millis ->
            val dateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
            return dateTime.format(outputFormatter)
        }

        return text
    } catch (_: Exception) {
        return input.toString()
    }
}


@OptIn(FormatStringsInDatetimeFormats::class)
object DateUtils {

    private val tz =
        TimeZone.currentSystemDefault()


    private fun now(): LocalDateTime {

        val instant = Clock.System.now()

        return instant.toLocalDateTime(
            TimeZone.currentSystemDefault()
        )
    }


    fun currentDate(
        format: String = "dd MMM yyyy"
    ): String {

        val formatter =
            LocalDateTime.Format {
                byUnicodePattern(format)
            }

        return now().format(formatter)
    }


    fun currentDateTime(
        format: String = "dd MMM yyyy hh:mm a"
    ): String {

        val formatter =
            LocalDateTime.Format {
                byUnicodePattern(format)
            }

        return now().format(formatter)
    }


    fun currentYear(): String =
        now().year.toString()


    fun currentMonthNumber(): String =
        now().month.number
            .toString()
            .padStart(2, '0')


    fun currentMonthName(): String =
        now().month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }


    fun currentDay(): String =
        now().day
            .toString()
            .padStart(2, '0')

}

fun currentDate() =
    DateUtils.currentDate()

fun currentDateTime() =
    DateUtils.currentDateTime()

fun currentYear() =
    DateUtils.currentYear()

fun currentMonthNumber() =
    DateUtils.currentMonthNumber()

fun currentMonthName() =
    DateUtils.currentMonthName()

fun currentDay() =
    DateUtils.currentDay()