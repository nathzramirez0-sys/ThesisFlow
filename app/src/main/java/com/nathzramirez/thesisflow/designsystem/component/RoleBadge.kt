package com.nathzramirez.thesisflow.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.domain.model.Role

@Composable
fun RoleBadge(role: Role, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (role) {
        Role.LEADER -> colors.primaryContainer to colors.onPrimaryContainer
        Role.MEMBER -> colors.secondaryContainer to colors.onSecondaryContainer
        Role.ADVISER -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Surface(modifier = modifier, color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text = roleLabel(role),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun roleLabel(role: Role): String = stringResource(
    when (role) {
        Role.LEADER -> R.string.role_leader
        Role.MEMBER -> R.string.role_member
        Role.ADVISER -> R.string.role_adviser
    },
)
