package com.nathzramirez.thesisflow.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.FormTextField
import com.nathzramirez.thesisflow.domain.validation.Field
import com.nathzramirez.thesisflow.ui.messageFor

/** Name, course and school fields, shared by onboarding and the profile screen. */
@Composable
fun ProfileForm(
    state: ProfileUiState,
    onDisplayNameChange: (String) -> Unit,
    onCourseChange: (String) -> Unit,
    onSchoolChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !state.isSaving && !state.isLoading
    val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            label = stringResource(R.string.field_display_name),
            error = state.fieldErrors.messageFor(Field.DISPLAY_NAME),
            enabled = enabled,
            keyboardOptions = words,
            autofillType = ContentType.PersonFullName,
        )
        FormTextField(
            value = state.course,
            onValueChange = onCourseChange,
            label = stringResource(R.string.field_course),
            hint = stringResource(R.string.field_course_hint),
            error = state.fieldErrors.messageFor(Field.COURSE),
            enabled = enabled,
            keyboardOptions = words,
        )
        FormTextField(
            value = state.school,
            onValueChange = onSchoolChange,
            label = stringResource(R.string.field_school),
            hint = stringResource(R.string.field_school_hint),
            error = state.fieldErrors.messageFor(Field.SCHOOL),
            enabled = enabled,
            keyboardOptions = words.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
    }
}
