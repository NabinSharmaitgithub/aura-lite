package com.aura.aura_ui.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Typed wrapper over SharedPreferences("aura_settings").
 *
 * No kill-switch, version-gate, auth, sync or firebase keys exist in this app by design.
 */
@Singleton
class AuraSettings @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        onboardingComplete = onboardingComplete,
        onboardingStep = onboardingStep,
        userName = userName,
        wakeWordEnabled = wakeWordEnabled,
        companionEnabled = companionEnabled,
        screenCaptureEnabled = screenCaptureEnabled,
        readScreenImageEnabled = readScreenImageEnabled,
        recordableVoiceEnabled = recordableVoiceEnabled,
        uiStreamEnabled = uiStreamEnabled,
        edgeGlowEnabled = edgeGlowEnabled,
        learningsEnabled = learningsEnabled,
        diagnosticsEnabled = diagnosticsEnabled,
        developerModeEnabled = developerModeEnabled,
        logRetentionDays = logRetentionDays,
        llmEndpointId = llmEndpointId,
        llmModelId = llmModelId,
        temperature = temperature,
        ttsVoice = ttsVoice,
        vadSensitivity = vadSensitivity,
        mcpEnabled = mcpEnabled,
        mcpPort = mcpPort,
        darkMode = darkMode,
        systemPromptOverride = systemPromptOverride,
        hookInstructions = hookInstructions,
        maxSteps = maxSteps,
        customEndpointJson = customEndpointJson,
    )

    var onboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) = prefs.edit().putBoolean("onboarding_complete", value).also { bump() }.apply()

    var onboardingStep: Int
        get() = prefs.getInt("onboarding_step", 0)
        set(value) {
            prefs.edit().putInt("onboarding_step", value.coerceIn(0, 9)).apply()
            bump()
        }

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(value) {
            prefs.edit().putString("user_name", value).apply()
            bump()
        }

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean("wake_word_enabled", false)
        set(value) {
            prefs.edit().putBoolean("wake_word_enabled", value).apply()
            bump()
        }

    var companionEnabled: Boolean
        get() = prefs.getBoolean("companion_enabled", false)
        set(value) {
            prefs.edit().putBoolean("companion_enabled", value).apply()
            bump()
        }

    var screenCaptureEnabled: Boolean
        get() = prefs.getBoolean("screen_capture_enabled", false)
        set(value) {
            prefs.edit().putBoolean("screen_capture_enabled", value).apply()
            bump()
        }

    var readScreenImageEnabled: Boolean
        get() = prefs.getBoolean("read_screen_image_enabled", false)
        set(value) {
            prefs.edit().putBoolean("read_screen_image_enabled", value).apply()
            bump()
        }

    var recordableVoiceEnabled: Boolean
        get() = prefs.getBoolean("recordable_voice_enabled", false)
        set(value) {
            prefs.edit().putBoolean("recordable_voice_enabled", value).apply()
            bump()
        }

    var uiStreamEnabled: Boolean
        get() = prefs.getBoolean("ui_stream_enabled", false)
        set(value) {
            prefs.edit().putBoolean("ui_stream_enabled", value).apply()
            bump()
        }

    var edgeGlowEnabled: Boolean
        get() = prefs.getBoolean("edge_glow_enabled", false)
        set(value) {
            prefs.edit().putBoolean("edge_glow_enabled", value).apply()
            bump()
        }

    var learningsEnabled: Boolean
        get() = prefs.getBoolean("learnings_enabled", true)
        set(value) {
            prefs.edit().putBoolean("learnings_enabled", value).apply()
            bump()
        }

    var diagnosticsEnabled: Boolean
        get() = prefs.getBoolean("diagnostics_enabled", false)
        set(value) {
            prefs.edit().putBoolean("diagnostics_enabled", value).apply()
            bump()
        }

    var developerModeEnabled: Boolean
        get() = prefs.getBoolean("developer_mode_enabled", false)
        set(value) {
            prefs.edit().putBoolean("developer_mode_enabled", value).apply()
            bump()
        }

    var logRetentionDays: Int
        get() = prefs.getInt("log_retention_days", 7)
        set(value) {
            prefs.edit().putInt("log_retention_days", value.coerceIn(1, 90)).apply()
            bump()
        }

    var llmEndpointId: String
        get() = prefs.getString("llm_endpoint_id", "groq") ?: "groq"
        set(value) {
            prefs.edit().putString("llm_endpoint_id", value).apply()
            bump()
        }

    var llmModelId: String
        get() = prefs.getString("llm_model_id", "") ?: ""
        set(value) {
            prefs.edit().putString("llm_model_id", value).apply()
            bump()
        }

    var temperature: Float
        get() = prefs.getFloat("temperature", 0.2f)
        set(value) {
            prefs.edit().putFloat("temperature", value.coerceIn(0f, 2f)).apply()
            bump()
        }

    var ttsVoice: String
        get() = prefs.getString("tts_voice", "") ?: ""
        set(value) {
            prefs.edit().putString("tts_voice", value).apply()
            bump()
        }

    var vadSensitivity: Int
        get() = prefs.getInt("vad_sensitivity", 2)
        set(value) {
            prefs.edit().putInt("vad_sensitivity", value.coerceIn(0, 4)).apply()
            bump()
        }

    var mcpEnabled: Boolean
        get() = prefs.getBoolean("mcp_enabled", false)
        set(value) {
            prefs.edit().putBoolean("mcp_enabled", value).apply()
            bump()
        }

    var mcpPort: Int
        get() = prefs.getInt("mcp_port", 47821)
        set(value) {
            prefs.edit().putInt("mcp_port", value.coerceIn(1024, 65535)).apply()
            bump()
        }

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) {
            prefs.edit().putBoolean("dark_mode", value).apply()
            bump()
        }

    var systemPromptOverride: String
        get() = prefs.getString("system_prompt_override", "") ?: ""
        set(value) {
            prefs.edit().putString("system_prompt_override", value).apply()
            bump()
        }

    var hookInstructions: String
        get() = prefs.getString("hook_instructions", "") ?: ""
        set(value) {
            prefs.edit().putString("hook_instructions", value).apply()
            bump()
        }

    var maxSteps: Int
        get() = prefs.getInt("max_steps", 24)
        set(value) {
            prefs.edit().putInt("max_steps", value.coerceIn(4, 60)).apply()
            bump()
        }

    var customEndpointJson: String
        get() = prefs.getString("custom_endpoint_json", "") ?: ""
        set(value) {
            prefs.edit().putString("custom_endpoint_json", value).apply()
            bump()
        }

    /** Destructive only from Settings → Privacy, never implicitly. */
    fun resetOnboarding() {
        prefs.edit()
            .putBoolean("onboarding_complete", false)
            .putInt("onboarding_step", 0)
            .apply()
        bump()
    }

    /** Used by tests and the developer screen. */
    fun clearAll() {
        prefs.edit().clear().apply()
        bump()
    }

    companion object {
        const val FILE = "aura_settings"
    }
}

data class SettingsSnapshot(
    val onboardingComplete: Boolean,
    val onboardingStep: Int,
    val userName: String,
    val wakeWordEnabled: Boolean,
    val companionEnabled: Boolean,
    val screenCaptureEnabled: Boolean,
    val readScreenImageEnabled: Boolean,
    val recordableVoiceEnabled: Boolean,
    val uiStreamEnabled: Boolean,
    val edgeGlowEnabled: Boolean,
    val learningsEnabled: Boolean,
    val diagnosticsEnabled: Boolean,
    val developerModeEnabled: Boolean,
    val logRetentionDays: Int,
    val llmEndpointId: String,
    val llmModelId: String,
    val temperature: Float,
    val ttsVoice: String,
    val vadSensitivity: Int,
    val mcpEnabled: Boolean,
    val mcpPort: Int,
    val darkMode: Boolean,
    val systemPromptOverride: String,
    val hookInstructions: String,
    val maxSteps: Int,
    val customEndpointJson: String,
)
