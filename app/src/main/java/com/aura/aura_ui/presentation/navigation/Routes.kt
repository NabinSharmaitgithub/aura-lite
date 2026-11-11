package com.aura.aura_ui.presentation.navigation

object Routes {
    const val HOME = "home"
    const val AGENT = "agent"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"

    const val AGENT_BRAIN = "agent/brain"
    const val AGENT_TOOLS = "agent/tools"
    const val AGENT_HOOKS = "agent/hooks"
    const val AGENT_SKILLS = "agent/skills"
    const val AGENT_MEMORY = "agent/memory"
    const val AGENT_COMPANION = "agent/companion"
    const val AGENT_VOICE = "agent/voice"

    const val SETTINGS_TAVILY = "settings/tavily"
    const val SETTINGS_TRUSTED_DEVICES = "settings/trusted_devices"
    const val SETTINGS_RESTRICTED_APPS = "settings/restricted_apps"
    const val SETTINGS_PERMISSIONS = "settings/permissions"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_TERMS = "settings/terms"
    const val SETTINGS_LICENSES = "settings/licenses"

    const val EVAL_SUITE = "eval"
    const val ACTIVITY_SESSION = "activity/session/{sessionId}"
    const val ACTIVITY_RETENTION = "activity/retention"

    fun session(sessionId: String) = "activity/session/$sessionId"

    val topLevel = setOf(HOME, AGENT, ACTIVITY, SETTINGS)

    val titles: Map<String, String> = mapOf(
        HOME to "Home",
        AGENT to "Agent",
        ACTIVITY to "Activity",
        SETTINGS to "Settings",
        AGENT_BRAIN to "Brain",
        AGENT_TOOLS to "Tools",
        AGENT_HOOKS to "Hooks",
        AGENT_SKILLS to "Skills",
        AGENT_MEMORY to "Memory",
        AGENT_COMPANION to "Companion",
        AGENT_VOICE to "Voice",
        SETTINGS_TAVILY to "Search key",
        SETTINGS_TRUSTED_DEVICES to "Trusted devices",
        SETTINGS_RESTRICTED_APPS to "Restricted apps",
        SETTINGS_PERMISSIONS to "Permissions",
        SETTINGS_PRIVACY to "Privacy",
        SETTINGS_TERMS to "Terms",
        SETTINGS_LICENSES to "Licences",
        EVAL_SUITE to "Eval suite",
        ACTIVITY_RETENTION to "Log retention",
    )

    fun titleFor(route: String?): String {
        if (route == null) return "AURA"
        titles[route]?.let { return it }
        if (route.startsWith("activity/session/")) return "Session"
        return route.substringAfterLast('/').replaceFirstChar { it.uppercase() }
    }
}
