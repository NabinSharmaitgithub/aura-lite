package com.aura.aura_ui.presentation.permissions

import android.Manifest

/**
 * Centralized permission metadata registry: one source for feature → permission requirements,
 * rationale, fix paths, and feature gating.
 */
object PermissionRegistry {

    data class Entry(
        val permission: String,
        val label: String,
        val purpose: String,
        val fixPath: String,
        val requiredFor: Set<String>,
        val optional: Boolean = false,
    )

    val entries: List<Entry> = listOf(
        Entry(
            permission = Manifest.permission.RECORD_AUDIO,
            label = "Microphone",
            purpose = "Speech-to-text, wake word and voice runs",
            fixPath = "settings/app_permissions/microphone",
            requiredFor = setOf("voice", "wake_word"),
        ),
        Entry(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            label = "Notifications",
            purpose = "Run controls, HITL questions, foreground service status",
            fixPath = "settings/app_permissions/notifications",
            requiredFor = setOf("agent_runs", "hitl"),
            optional = true,
        ),
        Entry(
            permission = Manifest.permission.READ_CONTACTS,
            label = "Contacts",
            purpose = "Resolve contacts for calls/messages",
            fixPath = "settings/app_permissions/contacts",
            requiredFor = setOf("resolve_contact", "dial", "compose_sms"),
            optional = true,
        ),
        Entry(
            permission = Manifest.permission.CAMERA,
            label = "Camera",
            purpose = "Optional visual features",
            fixPath = "settings/app_permissions/camera",
            requiredFor = setOf("camera"),
            optional = true,
        ),
        Entry(
            permission = Manifest.permission.SYSTEM_ALERT_WINDOW,
            label = "Display over other apps",
            purpose = "Companion overlay and pause pill",
            fixPath = "settings/overlay",
            requiredFor = setOf("overlay"),
            optional = true,
        ),
    )

    fun forFeature(feature: String): List<Entry> = entries.filter { feature in it.requiredFor }
    fun byPermission(permission: String): Entry? = entries.firstOrNull { it.permission == permission }
    fun featureEnabled(feature: String, granted: (String) -> Boolean): Boolean =
        forFeature(feature).filterNot { it.optional }.all { granted(it.permission) }
}
