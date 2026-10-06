package com.nathzramirez.thesisflow.ui

import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nathzramirez.thesisflow.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock-face picker in a dialog, following the phone's 12/24-hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayPickerDialog(
    title: String,
    initial: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * A day, then a time, for events like a defense. The Material date picker works
 * in UTC midnights; the result is that day and time in the phone's time zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    timeTitle: String,
    current: Instant?,
    defaultTime: LocalTime,
    onPick: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val currentLocal = current?.atZone(zone)
    // Saved as epoch days, so the step survives rotation.
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }

    val day = pickedDay
    if (day == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = currentLocal?.toLocalDate()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = dateState.selectedDateMillis ?: return@TextButton
                        pickedDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    },
                    enabled = dateState.selectedDateMillis != null,
                ) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        TimeOfDayPickerDialog(
            title = timeTitle,
            initial = currentLocal?.toLocalTime() ?: defaultTime,
            onPick = { time -> onPick(LocalDate.ofEpochDay(day).atTime(time).atZone(zone).toInstant()) },
            onDismiss = onDismiss,
        )
    }
}
