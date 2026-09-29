package com.nathzramirez.thesisflow.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.GhostButton
import com.nathzramirez.thesisflow.designsystem.component.HudLabel
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError
import kotlinx.coroutines.launch

/**
 * The app icon as a glowing gradient tile. The launcher foreground keeps its art
 * inside the adaptive-icon safe zone (the middle 60%), so it's scaled up here.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val aurora = AuroraTheme.colors
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier = modifier
            .size(size)
            .shadow(24.dp, shape, ambientColor = aurora.glow, spotColor = aurora.glow)
            .clip(shape)
            .background(aurora.brandGradient),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .scale(1.6f),
        )
    }
}

/**
 * Brand mark, app name, and a two-line headline whose second line carries the
 * brand gradient, e.g. "Your thesis, / in flow."
 */
@Composable
fun AuthHero(headlineStart: String, headlineAccent: String, subtitle: String, modifier: Modifier = Modifier) {
    val gradient = AuroraTheme.colors.brandGradient
    Column(modifier = modifier.fillMaxWidth()) {
        BrandMark()
        Spacer(Modifier.height(28.dp))
        HudLabel(stringResource(R.string.app_name))
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                append(headlineStart)
                append("\n")
                withStyle(SpanStyle(brush = gradient)) { append(headlineAccent) }
            },
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(10.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "or" divider plus the Google button. Fetches the token, then hands it to [onIdToken]. */
@Composable
fun GoogleSignInSection(
    enabled: Boolean,
    onIdToken: (String) -> Unit,
    onError: (DomainError) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
            HudLabel(stringResource(R.string.sign_in_or), modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        }
        GhostButton(
            text = stringResource(R.string.continue_with_google),
            onClick = {
                scope.launch {
                    when (val result = GoogleSignIn.requestIdToken(context)) {
                        is AppResult.Success -> onIdToken(result.value)
                        is AppResult.Failure -> onError(result.error)
                    }
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
