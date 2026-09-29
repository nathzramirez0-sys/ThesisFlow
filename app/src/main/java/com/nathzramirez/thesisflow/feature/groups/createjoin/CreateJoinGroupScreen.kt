package com.nathzramirez.thesisflow.feature.groups.createjoin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.AuroraScaffold
import com.nathzramirez.thesisflow.designsystem.component.AuroraTopBar
import com.nathzramirez.thesisflow.designsystem.component.FormTextField
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GradientButton
import com.nathzramirez.thesisflow.designsystem.component.SegmentedControl
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.ui.messageFor
import com.nathzramirez.thesisflow.ui.messageRes
import com.nathzramirez.thesisflow.ui.validationMessage

private const val TAB_CREATE = 0
private const val TAB_JOIN = 1

/** One screen, two modes. An invite link opens it in Join mode with the code filled in. */
@Composable
fun CreateJoinGroupScreen(
    initialInviteCode: String?,
    startOnJoin: Boolean,
    onBack: () -> Unit,
    onGroupReady: (String) -> Unit,
    createViewModel: CreateGroupViewModel = hiltViewModel(),
    joinViewModel: JoinGroupViewModel = hiltViewModel(),
) {
    val createState by createViewModel.uiState.collectAsStateWithLifecycle()
    val joinState by joinViewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable {
        mutableIntStateOf(if (startOnJoin || initialInviteCode != null) TAB_JOIN else TAB_CREATE)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(initialInviteCode) {
        if (initialInviteCode != null) joinViewModel.prefill(initialInviteCode)
    }
    LaunchedEffect(createState.createdGroupId) {
        val id = createState.createdGroupId ?: return@LaunchedEffect
        createViewModel.navigationHandled()
        onGroupReady(id)
    }
    LaunchedEffect(joinState.joinedGroupId) {
        val id = joinState.joinedGroupId ?: return@LaunchedEffect
        joinViewModel.navigationHandled()
        onGroupReady(id)
    }
    LaunchedEffect(createState.error, joinState.error) {
        val error = createState.error ?: joinState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(error.messageRes()))
        createViewModel.errorShown()
        joinViewModel.errorShown()
    }

    AuroraScaffold(
        topBar = { AuroraTopBar(title = stringResource(R.string.create_join_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SegmentedControl(
                    options = listOf(stringResource(R.string.tab_create), stringResource(R.string.tab_join)),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                )
                if (selectedTab == TAB_CREATE) {
                    CreateGroupForm(state = createState, viewModel = createViewModel)
                } else {
                    JoinGroupForm(state = joinState, viewModel = joinViewModel)
                }
            }
        }
    }
}

@Composable
private fun CreateGroupForm(state: CreateGroupUiState, viewModel: CreateGroupViewModel) {
    val enabled = !state.isSubmitting
    val sentence = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
    val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = stringResource(R.string.field_group_name),
                hint = stringResource(R.string.field_group_name_hint),
                error = state.fieldErrors.messageFor(Field.GROUP_NAME),
                enabled = enabled,
                keyboardOptions = sentence,
            )
            FormTextField(
                value = state.thesisTitle,
                onValueChange = viewModel::onThesisTitleChange,
                label = stringResource(R.string.field_thesis_title),
                error = state.fieldErrors.messageFor(Field.THESIS_TITLE),
                enabled = enabled,
                singleLine = false,
                maxLines = 3,
                keyboardOptions = sentence,
            )
            FormTextField(
                value = state.course,
                onValueChange = viewModel::onCourseChange,
                label = stringResource(R.string.field_course),
                error = state.fieldErrors.messageFor(Field.COURSE),
                enabled = enabled,
                keyboardOptions = words,
            )
            FormTextField(
                value = state.school,
                onValueChange = viewModel::onSchoolChange,
                label = stringResource(R.string.field_school),
                error = state.fieldErrors.messageFor(Field.SCHOOL),
                enabled = enabled,
                keyboardOptions = words.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.create() }),
            )
        }
    }
    Text(
        stringResource(R.string.create_group_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    GradientButton(
        text = stringResource(R.string.create_group),
        onClick = viewModel::create,
        loading = state.isSubmitting,
    )
}

@Composable
private fun JoinGroupForm(state: JoinGroupUiState, viewModel: JoinGroupViewModel) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.join_group_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                label = stringResource(R.string.field_invite_code),
                error = state.codeError?.let { validationMessage(Field.INVITE_CODE, it) },
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.join() }),
            )
        }
    }
    GradientButton(
        text = stringResource(R.string.join_group),
        onClick = viewModel::join,
        loading = state.isSubmitting,
    )
}
