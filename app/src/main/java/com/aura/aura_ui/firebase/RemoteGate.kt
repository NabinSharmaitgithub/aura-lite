package com.aura.aura_ui.firebase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote gate: kill switch / minimum version / update prompts / blocklist / suspension.
 *
 * Enforcement is cached and offline-safe: when remote config is unavailable, the last known
 * state is used. The old state carried five partially-overlapping booleans; [GateStatus] is now
 * the single source of truth and the booleans are derived.
 */
@Singleton
class RemoteGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfig: FirebaseRemoteConfigManager,
) {

    enum class GateStatus {
        UNKNOWN,
        OK,
        UPDATE_RECOMMENDED,
        UPDATE_REQUIRED,
        SUSPENDED,
        BLOCKLISTED,
        KILL_SWITCH,
    }

    data class GateState(
        val status: GateStatus = GateStatus.UNKNOWN,
        val reason: String? = null,
        val message: String? = null,
        val minVersionCode: Int = 0,
    ) {
        val blocked: Boolean
            get() = status == GateStatus.SUSPENDED ||
                status == GateStatus.BLOCKLISTED ||
                status == GateStatus.KILL_SWITCH
        val suspended: Boolean get() = status == GateStatus.SUSPENDED
        val blocklisted: Boolean get() = status == GateStatus.BLOCKLISTED
        val updateRequired: Boolean get() = status == GateStatus.UPDATE_REQUIRED
        val updateRecommended: Boolean
            get() = status == GateStatus.UPDATE_RECOMMENDED || status == GateStatus.UPDATE_REQUIRED
    }

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
            val status = when {
                killed -> GateStatus.KILL_SWITCH
                suspended -> GateStatus.SUSPENDED
                blocklisted -> GateStatus.BLOCKLISTED
                minVersion > 0 && currentVersion < minVersion -> GateStatus.UPDATE_REQUIRED
                else -> GateStatus.OK
            }
            val reason = when (status) {
                GateStatus.KILL_SWITCH -> "Remotely disabled by the maintainers"
                GateStatus.SUSPENDED -> "This installation has been remotely suspended"
                GateStatus.BLOCKLISTED -> "This installation is blocked from using AURA services"
                else -> cached.reason
            }
            cached = GateState(
                status = status,
                reason = reason,
                message = if (status == GateStatus.UPDATE_REQUIRED) "Please update AURA to continue." else cached.message,
                minVersionCode = minVersion,
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
        val status = when {
            blocklisted -> GateStatus.BLOCKLISTED
            suspended -> GateStatus.SUSPENDED
            blocked -> GateStatus.KILL_SWITCH
            cached.updateRequired -> GateStatus.UPDATE_REQUIRED
            else -> GateStatus.OK
        }
        cached = cached.copy(status = status, reason = reason)
    }

    fun ensureFresh(maxAgeMs: Long = 6 * 60 * 60 * 1000L) {
        if (System.currentTimeMillis() - lastRefreshAt > maxAgeMs) {
            // Refresh is best-effort; callers already use cached state.
        }
    }
}
