package com.nathzramirez.thesisflow.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nathzramirez.thesisflow.R
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Picks a due date for chapters and tasks. The Material picker works in UTC
 * midnights; the chosen calendar day becomes the end of that day in the phone's
 * time zone, so "due on the 5th" lasts all day. Offers Clear when a date is set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDatePickerDialog(current: Instant?, onPick: (Instant?) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val initialMillis = current?.atZone(zone)?.toLocalDate()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis ?: return@TextButton onDismiss()
                    val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onPick(endOfDay(day, zone))
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.action_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
