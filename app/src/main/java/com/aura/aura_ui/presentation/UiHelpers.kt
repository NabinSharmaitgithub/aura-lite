package com.aura.aura_ui.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Polls live system state instead of trusting a cached permission bit. Permission steps in
 * onboarding and the permissions screen both use this so they auto-advance without a restart.
 */
@Composable
fun rememberPermissions(vm: AuraViewModel): PermissionSnapshot {
    var snapshot by remember { mutableStateOf(vm.permissions()) }
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = vm.permissions()
            delay(1000)
        }
    }
    return snapshot
}

fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

fun Context.openIntent(action: String) {
    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

fun Context.openPackageSettings(permissionAction: String) {
    val intent = Intent(permissionAction, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

fun Long.formatDuration(): String {
    if (this <= 0) return "0s"
    val seconds = this / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

fun Long.formatBytes(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "%.1f KB".format(this / 1024.0)
    this < 1024L * 1024L * 1024L -> "%.1f MB".format(this / (1024.0 * 1024.0))
    else -> "%.1f GB".format(this / (1024.0 * 1024.0 * 1024.0))
}
