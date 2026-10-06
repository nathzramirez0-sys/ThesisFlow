package com.nathzramirez.thesisflow.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Whether the app may post notifications, and a way to ask. */
class NotificationAccess(val allowed: Boolean, val request: () -> Unit)

/**
 * Tracks notification permission, rechecked whenever the screen resumes (the user
 * may have changed it in Settings). The first request shows Android 13's dialog;
 * after a refusal Android stops showing it, so later requests open the app's
 * notification settings instead.
 */
@Composable
fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var askedBefore by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = notificationsAllowed(context)
        askedBefore = true
    }
    LifecycleResumeEffect(Unit) {
        allowed = notificationsAllowed(context)
        onPauseOrDispose { }
    }
    return NotificationAccess(allowed) {
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedBefore && !permissionGranted(context)
        if (canAsk) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(context)
    }
}

fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun notificationsAllowed(context: Context): Boolean =
    permissionGranted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun permissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
