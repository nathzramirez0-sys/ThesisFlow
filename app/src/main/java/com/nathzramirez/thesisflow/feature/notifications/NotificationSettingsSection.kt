package com.nathzramirez.thesisflow.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.GhostButton
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.SectionHeader
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.ui.TimeOfDayPickerDialog
import com.nathzramirez.thesisflow.ui.openNotificationSettings
import com.nathzramirez.thesisflow.ui.rememberNotificationAccess
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The profile's Notifications section: system permission, the two switches, and the reminder time. */
@Composable
fun NotificationSettingsSection(
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    val context = LocalContext.current
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val current = settings ?: return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.notifications_title))
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
            if (!access.allowed) {
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlowDot(AuroraTheme.colors.statusRevisions, size = 10.dp)
                    Text(
                        stringResource(R.string.notifications_blocked),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = access.request) { Text(stringResource(R.string.notifications_turn_on)) }
                }
                Divider()
            }
            SwitchRow(
                title = stringResource(R.string.notifications_push),
                body = stringResource(R.string.notifications_push_body),
                checked = current.pushEnabled,
                onCheckedChange = viewModel::setPushEnabled,
            )
            Divider()
            SwitchRow(
                title = stringResource(R.string.notifications_reminders),
                body = stringResource(R.string.notifications_reminders_body, timeFormatter.format(current.reminderTime)),
                checked = current.remindersEnabled,
                onCheckedChange = viewModel::setRemindersEnabled,
            )
            if (current.remindersEnabled) {
                LinkRow(
                    title = stringResource(R.string.notifications_reminder_time),
                    value = timeFormatter.format(current.reminderTime),
                    onClick = { pickingTime = true },
                )
            }
            Divider()
            LinkRow(
                title = stringResource(R.string.notifications_categories),
                value = null,
                onClick = { openNotificationSettings(context) },
            )
        }
    }

    if (pickingTime) {
        TimeOfDayPickerDialog(
            title = stringResource(R.string.notifications_reminder_time),
            initial = current.reminderTime,
            onPick = { time: LocalTime ->
                pickingTime = false
                viewModel.setReminderTime(time)
            },
            onDismiss = { pickingTime = false },
        )
    }
}

/**
 * Shown above the group list until notifications are allowed or the card is
 * dismissed: the reason first, then Android's own permission dialog.
 */
@Composable
fun NotificationPromptCard(
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    if (access.allowed || settings?.permissionPromptDismissed != false) return

    GlassCard(modifier = modifier.fillMaxWidth(), strong = true) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = AuroraTheme.colors.gradientEnd,
                modifier = Modifier.size(28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.notifications_prompt_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.notifications_prompt_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GhostButton(
                text = stringResource(R.string.notifications_not_now),
                onClick = { viewModel.dismissPrompt() },
                modifier = Modifier.weight(1f),
            )
            GradientButton(
                text = stringResource(R.string.notifications_turn_on),
                onClick = access.request,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The row handles the toggle, so the switch itself takes no clicks or focus.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LinkRow(title: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = AuroraTheme.colors.gradientEnd)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
