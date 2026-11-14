package com.aura.mcp.bridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Category assigned to a restricted app — purely descriptive for the settings UI.
 * Enforcement never branches on this, only on [RestrictedTier].
 */
@Serializable
enum class RestrictedAppCategory {
    BANKING, PAYMENT, TRADING_CRYPTO, AUTH_PASSWORD, OTHER
}

/**
 * How AURA behaves around this app.
 *
 * [DECLINE_ONLY] — AURA's own accessibility service stays on; the agent simply
 * refuses to act while the app is foreground (fast-path decline before any LLM
 * call, not a mid-run tool failure).
 *
 * [DISABLE_ACCESSIBILITY] — AURA additionally calls `disableSelf()` the moment
 * the app comes to the foreground, so device-level "please disable accessibility"
 * checks (common in banking/UPI apps) never trip. One-way per session: Android
 * gives no API to re-enable programmatically, so this tier is reserved for apps
 * confirmed to actually enforce that check.
 */
@Serializable
enum class RestrictedTier {
    DECLINE_ONLY,
    DISABLE_ACCESSIBILITY,
}

/** Where an entry came from — the UI shows LLM suggestions differently from user picks. */
@Serializable
enum class RestrictedAppSource { LLM_SUGGESTED, USER_ADDED }

@Serializable
data class RestrictedAppEntry(
    val packageName: String,
    val appName: String,
    val category: RestrictedAppCategory = RestrictedAppCategory.OTHER,
    val tier: RestrictedTier = RestrictedTier.DECLINE_ONLY,
    val source: RestrictedAppSource = RestrictedAppSource.USER_ADDED,
    val addedAt: Long = 0L,
)

/**
 * User-managed list of apps AURA should stay out of, persisted as one JSON blob.
 */
interface RestrictedAppsStore {
    val entries: StateFlow<List<RestrictedAppEntry>> get() = MutableStateFlow(emptyList())
    fun blockedPackages(): Set<String> = emptySet()
    fun isBlocked(packageName: String): Boolean = blockedPackages().contains(packageName)
    fun contains(packageName: String?): Boolean = !packageName.isNullOrBlank() && isBlocked(packageName)
    fun entryFor(packageName: String?): RestrictedAppEntry? = null
    fun upsert(entry: RestrictedAppEntry) {}
    fun remove(packageName: String) {}
    fun updateTier(packageName: String, tier: RestrictedTier) {}
    fun mergeSuggestions(suggestions: List<RestrictedAppEntry>) {}
    fun clearAll() {}
}

open class RestrictedAppsStoreImpl(
    private val load: () -> String?,
    private val save: (String) -> Unit,
) : RestrictedAppsStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private val _entries = MutableStateFlow(readInitial())
    override val entries: StateFlow<List<RestrictedAppEntry>> = _entries.asStateFlow()

    override fun contains(packageName: String?): Boolean =
        !packageName.isNullOrBlank() && _entries.value.any { it.packageName == packageName }

    override fun isBlocked(packageName: String): Boolean = contains(packageName)

    override fun blockedPackages(): Set<String> = _entries.value.map { it.packageName }.toSet()

    override fun entryFor(packageName: String?): RestrictedAppEntry? =
        packageName?.let { pkg -> _entries.value.firstOrNull { it.packageName == pkg } }

    override fun upsert(entry: RestrictedAppEntry) {
        synchronized(lock) {
            val others = _entries.value.filterNot { it.packageName == entry.packageName }
            persist(others + entry)
        }
    }

    override fun remove(packageName: String) {
        synchronized(lock) {
            persist(_entries.value.filterNot { it.packageName == packageName })
        }
    }

    override fun updateTier(packageName: String, tier: RestrictedTier) {
        synchronized(lock) {
            if (_entries.value.none { it.packageName == packageName }) return
            persist(_entries.value.map { if (it.packageName == packageName) it.copy(tier = tier) else it })
        }
    }

    override fun mergeSuggestions(suggestions: List<RestrictedAppEntry>) {
        synchronized(lock) {
            val existingPackages = _entries.value.map { it.packageName }.toSet()
            val fresh = suggestions.filterNot { it.packageName in existingPackages }
            if (fresh.isEmpty()) return
            persist(_entries.value + fresh)
        }
    }

    override fun clearAll() {
        synchronized(lock) { persist(emptyList()) }
    }

    private fun persist(entries: List<RestrictedAppEntry>) {
        _entries.value = entries
        runCatching { save(json.encodeToString(entries)) }
    }

    private fun readInitial(): List<RestrictedAppEntry> {
        val raw = load() ?: return emptyList()
        return runCatching { json.decodeFromString<List<RestrictedAppEntry>>(raw) }
            .getOrElse { emptyList() }
    }
}

fun RestrictedAppsStore(
    load: () -> String? = { null },
    save: (String) -> Unit = {},
): RestrictedAppsStore = RestrictedAppsStoreImpl(load, save)

val AllowAllRestrictedApps: RestrictedAppsStore = object : RestrictedAppsStore {
    override fun contains(packageName: String?) = false
    override fun isBlocked(packageName: String) = false
    override fun blockedPackages(): Set<String> = emptySet()
}
