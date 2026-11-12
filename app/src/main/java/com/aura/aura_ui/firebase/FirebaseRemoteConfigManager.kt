package com.aura.aura_ui.firebase

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Remote Config with baked-in defaults; only meaningful when the build ships Firebase.
 * AURA reads exactly one operational key (`aura_sync_paused`) as an emergency pause for cloud
 * sync — feature flags and A/B tests are deliberately not part of the product surface.
 */
@Singleton
class FirebaseRemoteConfigManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun config(): FirebaseRemoteConfig? {
        if (!FirebaseOptional.available(context)) return null
        return runCatching {
            FirebaseRemoteConfig.getInstance().apply {
                setConfigSettingsAsync(
                    FirebaseRemoteConfigSettings.Builder()
                        .setMinimumFetchIntervalInSeconds(3600L)
                        .build(),
                )
                setDefaultsAsync(
                    mapOf(
                        "aura_sync_paused" to false,
                        "kill_switch_enabled" to false,
                        "min_supported_version_code" to 0L,
                    ),
                )
            }
        }.getOrNull()
    }

    /** Fetches and activates config in the background; never blocks the caller. */
    suspend fun fetchAndActivate(): Result<Boolean> = runCatching {
        val remote = config() ?: return@runCatching false
        remote.fetchAndActivate().await()
    }

    fun syncPaused(): Boolean =
        config()?.getBoolean("aura_sync_paused") ?: false

    fun killSwitchEnabled(): Boolean =
        config()?.getBoolean("kill_switch_enabled") ?: false

    fun minSupportedVersionCode(): Long =
        config()?.getLong("min_supported_version_code") ?: 0L
}
