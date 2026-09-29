package com.nathzramirez.thesisflow.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.AuroraTopBar
import com.nathzramirez.thesisflow.designsystem.component.Avatar
import com.nathzramirez.thesisflow.designsystem.component.GhostButton
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.ui.messageRes

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.saved, state.error) {
        val message = when {
            state.saved -> context.getString(R.string.profile_saved)
            state.error != null -> context.getString(state.error!!.messageRes())
            else -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(message)
        viewModel.messageShown()
    }

    AuroraScaffold(
        topBar = { AuroraTopBar(title = stringResource(R.string.profile_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(name = state.displayName, photoUrl = state.photoUrl, size = 96.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        state.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    ProfileForm(
                        state = state,
                        onDisplayNameChange = viewModel::onDisplayNameChange,
                        onCourseChange = viewModel::onCourseChange,
                        onSchoolChange = viewModel::onSchoolChange,
                        onDone = viewModel::save,
                    )
                }
                GradientButton(
                    text = stringResource(R.string.action_save),
                    onClick = viewModel::save,
                    loading = state.isSaving,
                    enabled = !state.isLoading,
                )
                GhostButton(
                    text = stringResource(R.string.action_sign_out),
                    onClick = viewModel::signOut,
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    contentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
