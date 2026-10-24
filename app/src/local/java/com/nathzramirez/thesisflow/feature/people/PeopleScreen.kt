package com.nathzramirez.thesisflow.feature.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.Avatar
import com.nathzramirez.thesisflow.designsystem.component.GhostButton
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.component.roleLabel
import com.nathzramirez.thesisflow.domain.model.Person
import com.nathzramirez.thesisflow.feature.auth.AuthHero
import com.nathzramirez.thesisflow.ui.messageRes

/**
 * Who is using the local build. Everyone shares this phone, so picking a person
 * is signing in, and switching person (from the profile) is how one phone plays
 * the leader, a member and the adviser in turn.
 */
@Composable
fun PeopleScreen(viewModel: PeopleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(error.messageRes()))
        viewModel.errorShown()
    }

    AuroraScaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = padding.calculateTopPadding() + 40.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                AuthHero(
                    headlineStart = stringResource(R.string.people_headline_start),
                    headlineAccent = stringResource(R.string.people_headline_accent),
                    subtitle = stringResource(R.string.people_subtitle),
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                )
            }
            val people = state.people.orEmpty()
            if (people.isNotEmpty()) {
                item {
                    Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                        Spacer(Modifier.height(8.dp))
                        HudLabel(stringResource(R.string.people_title))
                    }
                }
            }
            items(people, key = { it.uid }) { person ->
                PersonCard(
                    person = person,
                    enabled = !state.isBusy,
                    onClick = { viewModel.choose(person.uid) },
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                )
            }
            item {
                GradientButton(
                    text = stringResource(R.string.people_add),
                    onClick = viewModel::addPerson,
                    icon = Icons.Filled.Add,
                    loading = state.isBusy,
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                )
            }
            // Offered on a fresh install only, so it can't add a second Team SmartBin.
            if (state.people?.isEmpty() == true) {
                item {
                    SampleGroupCard(
                        enabled = !state.isBusy,
                        onAdd = viewModel::addSampleGroup,
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonCard(person: Person, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val notSetUp = stringResource(R.string.people_not_set_up)
    GlassCard(modifier = modifier, onClick = onClick.takeIf { enabled }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(name = person.displayName.ifBlank { "?" }, photoUrl = null)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    person.displayName.ifBlank { notSetUp },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (person.memberships.isEmpty()) {
                        stringResource(R.string.people_no_groups)
                    } else {
                        // map is inline, so it can call a composable; joinToString's lambda can't.
                        person.memberships.map { membershipLabel(it) }.joinToString(", ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun membershipLabel(membership: Person.Membership): String =
    stringResource(R.string.people_membership, roleLabel(membership.role), membership.groupName)

@Composable
private fun SampleGroupCard(enabled: Boolean, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.people_sample_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.people_sample_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GhostButton(text = stringResource(R.string.people_sample_action), onClick = onAdd, enabled = enabled)
        }
    }
}
