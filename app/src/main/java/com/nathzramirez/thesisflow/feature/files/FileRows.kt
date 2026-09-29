package com.nathzramirez.thesisflow.feature.files

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.component.GlassCard
import com.nathzramirez.thesisflow.designsystem.component.GradientProgressBar
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.domain.model.FileAttachment
import com.nathzramirez.thesisflow.domain.model.PendingUpload
import com.nathzramirez.thesisflow.domain.model.UploadState
import com.nathzramirez.thesisflow.domain.result.DomainError
import com.nathzramirez.thesisflow.ui.messageRes
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** An upload still on the phone: waiting, in progress, or failed with Retry. */
@Composable
fun PendingUploadRow(upload: PendingUpload, onRetry: () -> Unit, onDiscard: () -> Unit) {
    val failed = upload.state == UploadState.FAILED
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                ImageVector.vectorResource(R.drawable.ic_document),
                contentDescription = null,
                tint = if (failed) AuroraTheme.colors.statusRevisions else AuroraTheme.colors.gradientEnd,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(upload.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (upload.state) {
                        UploadState.QUEUED -> stringResource(R.string.upload_waiting)
                        UploadState.UPLOADING -> stringResource(R.string.upload_uploading, upload.progressPercent)
                        UploadState.FAILED -> stringResource(
                            R.string.upload_failed,
                            stringResource((upload.error ?: DomainError.Unknown(null)).messageRes()),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) AuroraTheme.colors.statusRevisions else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (upload.state == UploadState.UPLOADING) GradientProgressBar(upload.progressPercent / 100f)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.upload_retry)) }
            if (upload.state != UploadState.UPLOADING) {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.upload_discard)) }
            }
        }
    }
}

/** A file already on the server; tap to open it. */
@Composable
fun AttachmentRow(file: FileAttachment, uploaderName: String?, isOpening: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    GlassCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(ImageVector.vectorResource(R.drawable.ic_document), contentDescription = null, tint = AuroraTheme.colors.gradientEnd)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(
                        R.string.version_by,
                        uploaderName ?: stringResource(R.string.version_former_member),
                        file.uploadedAt?.let(fileDateFormatter::format).orEmpty(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isOpening) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    Formatter.formatShortFileSize(context, file.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

val fileDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
