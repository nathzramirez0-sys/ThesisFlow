package com.nathzramirez.thesisflow.feature.groups.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.CountdownTiles
import com.nathzramirez.thesisflow.designsystem.component.CountdownUnit
import com.nathzramirez.thesisflow.designsystem.component.GhostButton
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.model.Countdown
import com.nathzramirez.thesisflow.domain.model.Defense
import com.nathzramirez.thesisflow.domain.model.DefenseKind
import com.nathzramirez.thesisflow.domain.model.DefenseSchedule
import com.nathzramirez.thesisflow.domain.model.Group
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The countdown to the next defense, ticking once a minute. Leaders tap it to
 * edit the dates; with none set, leaders see a prompt and others see nothing.
 */
@Composable
fun DefenseCard(group: Group, canEdit: Boolean, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val now by rememberMinuteClock()
    val defenses = DefenseSchedule.of(group)
    if (defenses.isEmpty()) {
        if (canEdit) SetupCard(onEdit, modifier)
        return
    }
    val next = DefenseSchedule.next(group, now)
    val aurora = AuroraTheme.colors

    GlassCard(onClick = if (canEdit) onEdit else null, strong = true, modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudLabel(
                    if (next != null) defenseTitle(next.kind) else stringResource(R.string.defense_label),
                    color = aurora.gradientEnd,
                    modifier = Modifier.weight(1f),
                )
                if (canEdit) {
                    Text(stringResource(R.string.defense_edit), style = MaterialTheme.typography.labelLarge, color = aurora.gradientEnd)
                }
            }
            if (next != null) {
                val left = Countdown.until(next.at, now)
                CountdownTiles(
                    units = listOf(
                        CountdownUnit(left.days, stringResource(R.string.countdown_days)),
                        CountdownUnit(left.hours.toLong(), stringResource(R.string.countdown_hours)),
                        CountdownUnit(left.minutes.toLong(), stringResource(R.string.countdown_minutes)),
                    ),
                    description = stringResource(
                        R.string.countdown_description, defenseTitle(next.kind), left.days, left.hours, left.minutes,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(formatWhen(next.at), style = MaterialTheme.typography.titleSmall)
                }
            } else {
                Text(stringResource(R.string.defense_all_done), style = MaterialTheme.typography.titleLarge)
            }
            defenses.filter { it != next }.forEach { OtherDefenseLine(it, isPast = !it.at.isAfter(now)) }
        }
    }
}

@Composable
private fun OtherDefenseLine(defense: Defense, isPast: Boolean) {
    val aurora = AuroraTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlowDot(if (isPast) aurora.statusApproved else aurora.statusNotStarted, size = 8.dp)
        Text(
            stringResource(
                if (isPast) R.string.defense_line_done else R.string.defense_line,
                defenseTitle(defense.kind),
                formatWhen(defense.at),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupCard(onEdit: () -> Unit, modifier: Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HudLabel(stringResource(R.string.defense_label), color = AuroraTheme.colors.gradientEnd)
            Text(stringResource(R.string.defense_setup_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.defense_setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GhostButton(
                text = stringResource(R.string.defense_setup_action),
                onClick = onEdit,
                icon = Icons.Filled.DateRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}

/** Leaders set, change or clear each defense here; picking a date opens the date-and-time picker. */
@Composable
fun DefenseDatesDialog(
    group: Group,
    onPick: (DefenseKind) -> Unit,
    onClear: (DefenseKind) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.defense_dialog_title)) },
        text = {
            Column {
                DefenseKind.entries.forEachIndexed { index, kind ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val at = when (kind) {
                        DefenseKind.PROPOSAL -> group.proposalDefenseAt
                        DefenseKind.FINAL -> group.finalDefenseAt
                    }
                    Column(modifier = Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(defenseTitle(kind), style = MaterialTheme.typography.titleSmall)
                        Text(
                            at?.let { formatWhen(it) } ?: stringResource(R.string.defense_not_set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row {
                            TextButton(onClick = { onPick(kind) }) {
                                Text(stringResource(if (at == null) R.string.defense_set else R.string.action_change))
                            }
                            if (at != null) {
                                TextButton(onClick = { onClear(kind) }) { Text(stringResource(R.string.action_clear)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
fun defenseTitle(kind: DefenseKind): String = stringResource(
    when (kind) {
        DefenseKind.PROPOSAL -> R.string.defense_title_proposal
        DefenseKind.FINAL -> R.string.defense_title_final
    },
)

/** The current time, updated at the top of every minute while on screen. */
@Composable
private fun rememberMinuteClock() = produceState(initialValue = Instant.now()) {
    while (isActive) {
        delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
        value = Instant.now()
    }
}

private fun formatWhen(at: Instant): String = whenFormatter.format(at.atZone(ZoneId.systemDefault()))

private const val MINUTE_MS = 60_000L

private val whenFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT)
