package com.nathzramirez.thesisflow.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.EmailField
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.PasswordField
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.ui.messageFor
import com.nathzramirez.thesisflow.ui.messageRes
import com.nathzramirez.thesisflow.ui.validationMessage

@Composable
fun LoginScreen(
    hasPendingInvite: Boolean,
    onCreateAccount: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.error, state.resetLinkSentTo) {
        val message = state.error?.let { context.getString(it.messageRes()) }
            ?: state.resetLinkSentTo?.let { context.getString(R.string.reset_sent, it) }
            ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.messageShown()
    }

    AuroraScaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.height(24.dp))
                AuthHero(
                    headlineStart = stringResource(R.string.auth_headline_start),
                    headlineAccent = stringResource(R.string.auth_headline_accent),
                    subtitle = stringResource(R.string.auth_subtitle),
                )
                if (hasPendingInvite) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlowDot(AuroraTheme.colors.gradientEnd)
                        Text(
                            stringResource(R.string.invite_waiting_sign_in),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuroraTheme.colors.gradientEnd,
                        )
                    }
                }

                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EmailField(
                            value = state.email,
                            onValueChange = viewModel::onEmailChange,
                            error = state.fieldErrors.messageFor(Field.EMAIL),
                            enabled = !state.isSubmitting,
                        )
                        PasswordField(
                            value = state.password,
                            onValueChange = viewModel::onPasswordChange,
                            label = stringResource(R.string.field_password),
                            error = state.fieldErrors.messageFor(Field.PASSWORD),
                            enabled = !state.isSubmitting,
                            imeAction = ImeAction.Done,
                            onImeAction = viewModel::signIn,
                        )
                        TextButton(
                            onClick = viewModel::openPasswordReset,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(R.string.sign_in_forgot_password))
                        }
                        GradientButton(
                            text = stringResource(R.string.sign_in),
                            onClick = viewModel::signIn,
                            loading = state.isSubmitting,
                        )
                    }
                }

                GoogleSignInSection(
                    enabled = !state.isSubmitting,
                    onIdToken = viewModel::signInWithGoogle,
                    onError = viewModel::onGoogleSignInFailed,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.sign_in_no_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCreateAccount) {
                        Text(stringResource(R.string.sign_in_create_account))
                    }
                }
            }
        }
    }

    state.passwordReset?.let { reset ->
        PasswordResetDialog(
            state = reset,
            onEmailChange = viewModel::onResetEmailChange,
            onSend = viewModel::sendResetLink,
            onDismiss = viewModel::dismissPasswordReset,
        )
    }
}

@Composable
private fun PasswordResetDialog(
    state: PasswordResetState,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.reset_body))
                EmailField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    error = state.error?.let { validationMessage(Field.EMAIL, it) },
                    enabled = !state.isSending,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !state.isSending) {
                Text(stringResource(R.string.reset_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
