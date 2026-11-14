package com.aura.aura_ui.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Detects Xiaomi / Redmi / HyperOS / MIUI devices and provides direct deep-links
 * to the exact MIUI Security Center settings needed to prevent MIUI from revoking
 * accessibility permissions on app close or swipe-from-recents.
 */
object MiuiHelper {
    private const val TAG = "MiuiHelper"

    /** Returns true if the device manufacturer is Xiaomi, Redmi, or POCO. */
    fun isXiaomiOrRedmi(): Boolean {
        val man = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        return man.contains("xiaomi") || man.contains("redmi") || man.contains("poco") ||
                brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
    }

    /**
     * Opens the MIUI Autostart Management screen.
     * On MIUI, any app without Autostart enabled has its accessibility service killed
     * and disabled as soon as the app process is closed.
     */
    fun openAutostartSettings(context: Context): Boolean {
        val intents = listOf(
            Intent().setComponent(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            ),
            Intent().setComponent(
                ComponentName("com.miui.securitycenter", "com.miui.securityscan.MainActivity")
            ),
            Intent().setComponent(
                ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
            ),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Successfully launched autostart intent: ${intent.component}")
                return true
            } catch (t: Throwable) {
                Log.w(TAG, "Failed launching ${intent.component}: ${t.message}")
            }
        }
        return false
    }

    /**
     * Opens MIUI Battery Saver / PowerKeeper settings.
     * Setting AURA to "No restrictions" ensures the background accessibility service
     * is never killed by MIUI Powerkeeper when idle.
     */
    fun openBatterySaverSettings(context: Context): Boolean {
        val intents = listOf(
            Intent().setComponent(
                ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            ).apply {
                putExtra("package_name", context.packageName)
                putExtra("package_label", "AURA")
            },
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Successfully launched battery settings: ${intent.component ?: intent.action}")
                return true
            } catch (t: Throwable) {
                Log.w(TAG, "Failed launching ${intent.component ?: intent.action}: ${t.message}")
            }
        }
        return false
    }

    /** Checks whether battery optimization is ignored. */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
}
