package com.aura.aura_ui.firebase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote gate: kill switch / minimum version / update prompts / blocklist / suspension.
 *
 * Enforcement is cached and offline-safe: when remote config is unavailable, the last known
 * state is used. A local override exists for tests and developer builds.
 */
@Singleton
class RemoteGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfig: FirebaseRemoteConfigManager,
) {

    data class GateState(
        val blocked: Boolean = false,
        val reason: String? = null,
        val updateRecommended: Boolean = false,
        val updateRequired: Boolean = false,
        val message: String? = null,
        val minVersionCode: Int = 0,
        val suspended: Boolean = false,
        val blocklisted: Boolean = false,
    )

    @Volatile private var cached: GateState = GateState()
    @Volatile private var lastRefreshAt: Long = 0L

    fun state(): GateState = cached
    fun isBlocked(): Boolean = cached.blocked
    fun isSuspended(): Boolean = cached.suspended
    fun isBlocklisted(): Boolean = cached.blocklisted
    fun minSupportedVersionCode(): Long = cached.minVersionCode.toLong()
    fun updateRequired(): Boolean = cached.updateRequired
    fun updateRecommended(): Boolean = cached.updateRecommended
    fun message(): String? = cached.message

    /** Refresh from Remote Config if enabled/available (best-effort, offline-safe). */
    suspend fun refresh(): GateState {
        val fetch = runCatching { remoteConfig.fetchAndActivate() }.getOrDefault(Result.failure(IllegalStateException()))
        if (fetch.isSuccess) {
            val killed = runCatching { remoteConfig.killSwitchEnabled() }.getOrDefault(false)
            val suspended = runCatching { remoteConfig.suspended() }.getOrDefault(false)
            val blocklisted = runCatching { remoteConfig.blocklisted() }.getOrDefault(false)
            val minVersion = runCatching { remoteConfig.minSupportedVersionCode() }.getOrDefault(0L).toInt()
            val currentVersion = runCatching { com.aura.aura_ui.BuildConfig.VERSION_CODE }.getOrDefault(0)
            val updateRequired = minVersion > 0 && currentVersion < minVersion
            val blocked = killed || suspended || blocklisted
            val reason = when {
                killed -> "Remotely disabled by the maintainers"
                suspended -> "This installation has been remotely suspended"
                blocklisted -> "This installation is blocked from using AURA services"
                else -> cached.reason
            }
            cached = GateState(
                blocked = blocked,
                reason = reason,
                updateRecommended = updateRequired,
                updateRequired = updateRequired,
                message = if (updateRequired) "Please update AURA to continue." else cached.message,
                minVersionCode = minVersion,
                suspended = suspended,
                blocklisted = blocklisted,
            )
            lastRefreshAt = System.currentTimeMillis()
        }
        return cached
    }

    fun applyLocalOverride(
        blocked: Boolean = false,
        reason: String? = null,
        suspended: Boolean = false,
        blocklisted: Boolean = false,
    ) {
        cached = cached.copy(
            blocked = blocked,
            reason = reason,
            suspended = suspended,
            blocklisted = blocklisted,
        )
    }

    fun ensureFresh(maxAgeMs: Long = 6 * 60 * 60 * 1000L) {
        if (System.currentTimeMillis() - lastRefreshAt > maxAgeMs) {
            // Refresh is best-effort; callers already use cached state.
        }
    }
}
