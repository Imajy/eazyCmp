package com.aj.shared.extension

import com.aj.shared.ui.formatDateCMP
import com.aj.shared.ui.tz
import io.ktor.util.date.getTimeMillis
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime


// --------------------
// EXTENSIONS
// --------------------

fun Any?.toDdMmmYyyy(
    format: String = "dd MMM yyyy"
) =
    formatDateCMP(this, format)


fun Any?.toYyyyMmDd(
    format: String = "yyyy/MM/dd"
) =
    formatDateCMP(this, format)


fun Any?.toServerDate(
    format: String = "yyyy-MM-dd"
) =
    formatDateCMP(this, format)


fun Any?.toDateTimeSeconds(
    format: String = "dd MMM yyyy hh:mm:ss a"
) =
    formatDateCMP(this, format)


fun Any?.toDateTime(
    format: String = "dd MMM yyyy hh:mm a"
) =
    formatDateCMP(this, format)



// --------------------
// CURRENT DATE
// --------------------

object DateUtils {

    private fun now(): LocalDateTime {

        val instant =
            Instant.fromEpochMilliseconds(
                getTimeMillis()
            )

        return instant.toLocalDateTime(tz)
    }


    fun currentDate(
        format: String = "dd MMM yyyy"
    ) =
        formatDateCMP(now(), format)


    fun currentDateTime(
        format: String = "dd MMM yyyy hh:mm a"
    ) =
        formatDateCMP(now(), format)


    fun currentYear() =
        now().year.toString()


    fun currentMonthNumber() =
        now().monthNumber
            .toString()
            .padStart(2, '0')


    fun currentMonthName() =
        now().month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }


    fun currentDay() =
        now().dayOfMonth
            .toString()
            .padStart(2, '0')
}



// --------------------
// GLOBAL HELPERS
// --------------------

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