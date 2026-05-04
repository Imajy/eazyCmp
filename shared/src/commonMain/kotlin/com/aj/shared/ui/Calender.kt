package com.aj.shared.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aj.shared.theme.whiteColor
import io.ktor.util.date.getTimeMillis
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn


enum class DateRestrictionType {
    NONE,
    PAST_ONLY,
    FUTURE_ONLY,
    CUSTOM_RANGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericDatePicker(
    show: Boolean,
    isRangePicker: Boolean = false,
    restrictionType: DateRestrictionType = DateRestrictionType.NONE,
    pastLimitDays: Int? = null,
    futureLimitDays: Int? = null,
    minDateMillis: Long? = null,
    maxDateMillis: Long? = null,
    onDismiss: () -> Unit,
    onDateSelected: (startDate: Long?, endDate: Long?) -> Unit
) {

    if (!show) return

    val now = getTimeMillis()

    val pastMin = pastLimitDays?.let { now - (it * 24 * 60 * 60 * 1000L) }
    val futureMax = futureLimitDays?.let { now + (it * 24 * 60 * 60 * 1000L) }

    val selectableDates = remember(
        restrictionType,
        pastLimitDays,
        futureLimitDays,
        minDateMillis,
        maxDateMillis
    ) {

        object : SelectableDates {

            override fun isSelectableDate(utcTimeMillis: Long): Boolean {

                val afterMin = when {
                    minDateMillis != null -> utcTimeMillis >= minDateMillis
                    pastMin != null -> utcTimeMillis >= pastMin
                    else -> true
                }

                val beforeMax = when {
                    maxDateMillis != null -> utcTimeMillis <= maxDateMillis
                    futureMax != null -> utcTimeMillis <= futureMax
                    else -> true
                }

                val restriction = when (restrictionType) {

                    DateRestrictionType.NONE -> true

                    DateRestrictionType.PAST_ONLY ->
                        utcTimeMillis <= now

                    DateRestrictionType.FUTURE_ONLY ->
                        utcTimeMillis >= now

                    DateRestrictionType.CUSTOM_RANGE ->
                        afterMin && beforeMax
                }

                return afterMin && beforeMax && restriction
            }
        }
    }

    if (isRangePicker) {

        val state = rememberDateRangePickerState(
            initialDisplayedMonthMillis = maxDateMillis ?: now,
            selectableDates = selectableDates
        )

        DatePickerDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(10.dp),
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateSelected(
                            state.selectedStartDateMillis,
                            state.selectedEndDateMillis
                        )

                        onDismiss()
                    }
                ) {
                    Text("OK", style = MaterialTheme.typography.bodyMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", style = MaterialTheme.typography.bodyMedium)
                }
            }
        ) {

            DateRangePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = whiteColor
                ),
                title = {
                    Text(
                        text = "Select Dates",
                        modifier = Modifier.padding(top = 15.dp, start = 15.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                headline = {
                    val startDate =
                        state.selectedStartDateMillis?.let { millis ->
                            formatDateMillis(millis)
                        } ?: "Start date"

                    val endDate =
                        state.selectedEndDateMillis?.let { millis ->
                            formatDateMillis(millis)
                        } ?: "End date"

                    Text(
                        text = "$startDate – $endDate",

                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),

                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                },
                showModeToggle = false
            )
        }

    } else {

        val state = rememberDatePickerState(
            initialDisplayedMonthMillis = maxDateMillis ?: now,
            selectableDates = selectableDates
        )

        DatePickerDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(10.dp),
            confirmButton = {

                TextButton(
                    onClick = {

                        onDateSelected(
                            state.selectedDateMillis,
                            null
                        )

                        onDismiss()
                    }
                ) {
                    Text("OK", style = MaterialTheme.typography.bodyMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", style = MaterialTheme.typography.bodyMedium)
                }
            }
        ) {

            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(containerColor = whiteColor),
                showModeToggle = false,
                title = {
                    Text(
                        text = "Select Date",
                        modifier = Modifier.padding(top = 15.dp, start = 15.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                headline = {

                    val date = state.selectedDateMillis?.let { millis -> formatDateMillis(millis) } ?: "Select Date"

                    Text(
                        text = date,

                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),

                        modifier = Modifier.padding(16.dp)
                    )
                },
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyDatePicker(
    show: Boolean,
    isRangePicker: Boolean = false,
    restrictionType: DateRestrictionType = DateRestrictionType.NONE,
    minDaysFromToday: Int? = null,
    maxDaysFromToday: Int? = null,
    selectedDateString: String? = null,
    useCurrentDateAsDefault: Boolean = false,
    onDismiss: () -> Unit,
    onDateSelected: (startDate: Long?, endDate: Long?) -> Unit
) {
    if (!show) return
    val now = getTimeMillis()
    val minDateMillis = remember(minDaysFromToday) {
        minDaysFromToday?.let {
            now + (it * 24 * 60 * 60 * 1000L)
        }
    }

    val maxDateMillis = remember(maxDaysFromToday) {
        maxDaysFromToday?.let {
            now + (it * 24 * 60 * 60 * 1000L)
        }
    }

    val selectableDates = remember(
        restrictionType,
        minDateMillis,
        maxDateMillis
    ) {
        object : SelectableDates {
            override fun isSelectableDate(
                utcTimeMillis: Long
            ): Boolean {
                val afterMin = minDateMillis?.let {
                    utcTimeMillis >= it
                } ?: true

                val beforeMax = maxDateMillis?.let {
                    utcTimeMillis <= it
                } ?: true

                val restrictionCheck = when (restrictionType) {
                    DateRestrictionType.NONE -> true

                    DateRestrictionType.PAST_ONLY ->
                        utcTimeMillis <= now

                    DateRestrictionType.FUTURE_ONLY ->
                        utcTimeMillis >= now

                    DateRestrictionType.CUSTOM_RANGE ->
                        afterMin && beforeMax
                }

                return afterMin && beforeMax && restrictionCheck
            }
        }
    }

    if (isRangePicker) {
        val state = rememberDateRangePickerState(
            initialDisplayedMonthMillis = maxDateMillis ?: now,
            selectableDates = selectableDates
        )

        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateSelected(
                            state.selectedStartDateMillis,
                            state.selectedEndDateMillis
                        )
                        onDismiss()
                    }
                ) {
                    Text(
                        "OK",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        ) {

            DateRangePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = whiteColor
                ),
                title = {
                    Text(
                        text = "Select Dates",
                        modifier = Modifier.padding(top = 15.dp, start = 15.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                headline = {
                    val startDate =
                        state.selectedStartDateMillis?.let { millis ->
                            formatDateMillis(millis)
                        } ?: "Start date"

                    val endDate =
                        state.selectedEndDateMillis?.let { millis ->
                            formatDateMillis(millis)
                        } ?: "End date"

                    Text(
                        text = "$startDate – $endDate",

                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),

                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                },
                showModeToggle = false
            )
        }
    } else {

        val parsedMillis = remember(selectedDateString) {
            parseDateStringToMillis(selectedDateString)
        }

        val initialSelectedDate = remember(
            parsedMillis,
            useCurrentDateAsDefault,
            minDateMillis,
            maxDateMillis
        ) {
            resolveInitialDateMillis(
                parsedMillis = parsedMillis,
                useCurrentDateAsDefault = useCurrentDateAsDefault,
                now = now,
                minDateMillis = minDateMillis,
                maxDateMillis = maxDateMillis
            )
        }

        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialSelectedDate,
            initialDisplayedMonthMillis = initialSelectedDate ?: (maxDateMillis ?: now),
            selectableDates = selectableDates
        )

        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateSelected(state.selectedDateMillis, null)
                        onDismiss()
                    }
                ) {
                    Text("OK", style = MaterialTheme.typography.bodyMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", style = MaterialTheme.typography.bodyMedium)
                }
            }
        ) {
            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = whiteColor
                ),
                headline = {
                    val date = state.selectedDateMillis
                        ?.let { millis -> formatDateMillis(millis) }
                        ?: "Select Date"

                    Text(
                        text = date,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                },
                title = {
                    Text(
                        text = "Select Date",
                        modifier = Modifier.padding(top = 15.dp, start = 15.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                showModeToggle = false
            )
        }
    }
}

fun parseDateStringToMillis(
    date: String?,
): Long? {
    return try {
        if (date.isNullOrEmpty()) return null

        val parts = date.split(" ")
        val day = parts[0].toInt()

        val month = Month.entries.first {
            it.name.take(3).equals(parts[1], ignoreCase = true)
        }

        val year = parts[2].toInt()

        // ✅ ALWAYS UTC
        LocalDate(year, month, day)
            .atStartOfDayIn(TimeZone.UTC)
            .toEpochMilliseconds()

    } catch (e: Exception) {
        null
    }
}

fun resolveInitialDateMillis(
    parsedMillis: Long?,
    useCurrentDateAsDefault: Boolean,
    now: Long,
    minDateMillis: Long?,
    maxDateMillis: Long?
): Long? {

    fun coerceInRange(value: Long): Long {
        var v = value
        if (minDateMillis != null && v < minDateMillis) v = minDateMillis
        if (maxDateMillis != null && v > maxDateMillis) v = maxDateMillis
        return v
    }

    return when {
        // ✅ valid parsed date
        parsedMillis != null -> coerceInRange(parsedMillis)

        // ✅ fallback to current date
        useCurrentDateAsDefault -> coerceInRange(now)

        // ❌ no default
        else -> null
    }
}