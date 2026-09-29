package com.nathzramirez.thesisflow.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.nathzramirez.thesisflow.designsystem.component.EmailField
import com.nathzramirez.thesisflow.designsystem.component.LoadingButton
import com.nathzramirez.thesisflow.designsystem.component.PasswordField
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.ui.messageFor
import com.nathzramirez.thesisflow.ui.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(error.messageRes()))
        viewModel.errorShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AuthHeader(
                    title = stringResource(R.string.sign_up_title),
                    subtitle = stringResource(R.string.sign_up_subtitle),
                )
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
                    hint = stringResource(R.string.password_rules),
                    enabled = !state.isSubmitting,
                    isNewPassword = true,
                    imeAction = ImeAction.Next,
                )
                PasswordField(
                    value = state.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = stringResource(R.string.field_confirm_password),
                    error = state.fieldErrors.messageFor(Field.CONFIRM_PASSWORD),
                    enabled = !state.isSubmitting,
                    isNewPassword = true,
                    imeAction = ImeAction.Done,
                    onImeAction = viewModel::signUp,
                )
                LoadingButton(
                    text = stringResource(R.string.sign_up),
                    onClick = viewModel::signUp,
                    loading = state.isSubmitting,
                )
                GoogleSignInSection(
                    enabled = !state.isSubmitting,
                    onIdToken = viewModel::signUpWithGoogle,
                    onError = viewModel::onGoogleSignInFailed,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.sign_up_have_account), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onBack) { Text(stringResource(R.string.sign_in)) }
                }
            }
        }
    }
}
