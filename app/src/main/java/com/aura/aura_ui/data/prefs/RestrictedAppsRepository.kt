package com.aura.aura_ui.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.aura.mcp.bridge.RestrictedAppsStore

/**
 * Per-app block list. AURA refuses to act while a blocked package is foreground; the refusal is
 * a normal, structured tool error rather than a crash or a silent skip.
 */
@Singleton
class RestrictedAppsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : RestrictedAppsStore {

    private val prefs = context.getSharedPreferences("aura_restricted_apps", Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    override fun isBlocked(packageName: String): Boolean =
        blockedPackages().contains(packageName)

    override fun blockedPackages(): Set<String> =
        prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun setBlocked(packageName: String, blocked: Boolean) {
        val next = blockedPackages().toMutableSet()
        if (blocked) next += packageName else next -= packageName
        prefs.edit().putStringSet(KEY, next).apply()
        _revision.value = _revision.value + 1
    }

    fun clear() {
        prefs.edit().clear().apply()
        _revision.value = _revision.value + 1
    }

    companion object {
        private const val KEY = "blocked_packages"
    }
}
