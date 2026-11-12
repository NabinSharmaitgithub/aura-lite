package com.aura.aura_ui.firebase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional, opt-in kill switch driven by Firebase Remote Config (`kill_switch_enabled`,
 * `min_supported_version_code`). When Firebase is absent or the user never linked an account,
 * the gate is a no-op and nothing is read from the network.
 */
@Singleton
class RemoteGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfig: FirebaseRemoteConfigManager,
) {
    fun isBlocked(): Boolean {
        if (!FirebaseOptional.available(context)) return false
        return runCatching { remoteConfig.killSwitchEnabled() }.getOrDefault(false)
    }

    fun minSupportedVersionCode(): Long {
        if (!FirebaseOptional.available(context)) return 0L
        return runCatching { remoteConfig.minSupportedVersionCode() }.getOrDefault(0L)
    }
}
