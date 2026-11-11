package com.aura.aura_ui.firebase

import android.content.Context
import android.os.Bundle
import com.aura.aura_ui.data.prefs.AuraSettings
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-safe diagnostics wrappers. Analytics events are limited to coarse, non-identifying
 * signals (sync outcomes, error counts) and never include user text. Crash collection only runs
 * when both the build ships Firebase and the user switched it on.
 */
@Singleton
class FirebaseDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AuraSettings,
) {
    private fun analytics(): FirebaseAnalytics? =
        if (FirebaseOptional.available(context) && prefs.firebaseAnalyticsEnabled) {
            runCatching { FirebaseAnalytics.getInstance(context) }.getOrNull()
        } else {
            null
        }

    private fun crashlytics(): FirebaseCrashlytics? =
        if (FirebaseOptional.available(context) && prefs.firebaseCrashlyticsEnabled) {
            runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()
        } else {
            null
        }

    fun applyCrashToggle() {
        if (!FirebaseOptional.available(context)) return
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(prefs.firebaseCrashlyticsEnabled)
        }
    }

    fun logSync(pushed: Int, pulled: Int, skipped: Int) {
        analytics()?.logEvent(
            "aura_sync",
            Bundle().apply {
                putInt("pushed", pushed)
                putInt("pulled", pulled)
                putInt("skipped", skipped)
            },
        )
    }

    fun recordError(tag: String, error: Throwable) {
        crashlytics()?.apply {
            setCustomKey("area", tag)
            recordException(error)
        }
    }
}
