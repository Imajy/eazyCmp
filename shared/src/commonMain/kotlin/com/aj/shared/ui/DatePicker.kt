package com.aj.shared.ui

import kotlinx.datetime.*
import kotlinx.datetime.format.*
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

internal val tz: TimeZone
    get() = TimeZone.currentSystemDefault()


// --------------------
// CORE FORMAT FUNCTION
// --------------------

@OptIn(FormatStringsInDatetimeFormats::class)
internal fun formatDateCMP(
    input: Any?,
    outputFormat: String
): String {

    if (input == null) return ""

    return try {

        when (input) {

            is Long -> {
                Instant.fromEpochMilliseconds(input)
                    .toLocalDateTime(tz)
                    .format(createDateTimeFormatter(outputFormat))
            }

            is Instant -> {
                input.toLocalDateTime(tz)
                    .format(createDateTimeFormatter(outputFormat))
            }

            is LocalDateTime -> {
                input.format(createDateTimeFormatter(outputFormat))
            }

            is LocalDate -> {
                input.format(createDateFormatter(outputFormat))
            }

            else -> {
                parseFromString(input.toString(), outputFormat)
            }
        }

    } catch (_: Exception) {

        input.toString()
    }
}



// --------------------
// STRING PARSER
// --------------------

@OptIn(FormatStringsInDatetimeFormats::class)
private fun parseFromString(
    raw: String,
    outputFormat: String
): String {

    val text = raw.trim()

    if (text.isEmpty()) return ""

    val patterns = listOf(

        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",

        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",

        "dd-MM-yyyy",
        "dd/MM/yyyy",
        "yyyy/MM/dd",

        "yyyyMMdd"
    )


    patterns.forEach { pattern ->

        // try datetime
        try {

            val formatter =
                LocalDateTime.Format {
                    byUnicodePattern(pattern)
                }

            val parsed =
                LocalDateTime.parse(text, formatter)

            return parsed.format(
                createDateTimeFormatter(outputFormat)
            )

        } catch (_: Exception) {}


        // try date
        try {

            val formatter =
                LocalDate.Format {
                    byUnicodePattern(pattern)
                }

            val parsed =
                LocalDate.parse(text, formatter)

            return parsed.format(
                createDateFormatter(outputFormat)
            )

        } catch (_: Exception) {}
    }


    // millis inside string
    text.toLongOrNull()?.let {

        return Instant
            .fromEpochMilliseconds(it)
            .toLocalDateTime(tz)
            .format(createDateTimeFormatter(outputFormat))
    }


    return text
}



// --------------------
// FORMATTER FACTORY
// --------------------

@OptIn(FormatStringsInDatetimeFormats::class)
private fun createDateTimeFormatter(
    pattern: String
): DateTimeFormat<LocalDateTime> {

    return try {

        LocalDateTime.Format {
            byUnicodePattern(pattern)
        }

    } catch (_: Exception) {

        LocalDateTime.Format {
            byUnicodePattern("dd-MM-yyyy")
        }
    }
}


@OptIn(FormatStringsInDatetimeFormats::class)
private fun createDateFormatter(
    pattern: String
): DateTimeFormat<LocalDate> {

    return try {

        LocalDate.Format {
            byUnicodePattern(pattern)
        }

    } catch (_: Exception) {

        LocalDate.Format {
            byUnicodePattern("dd-MM-yyyy")
        }
    }
}