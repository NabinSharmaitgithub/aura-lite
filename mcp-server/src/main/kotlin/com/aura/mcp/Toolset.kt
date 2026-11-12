package com.aura.mcp

/** Tool groups exposed to clients/runtimes. */
enum class Toolset {
    CORE,
    ASSISTANT,
    BROWSER;

    companion object {
        fun parse(value: String?): Toolset? = value?.trim()?.uppercase()?.let { v ->
            entries.firstOrNull { it.name == v }
        }

        fun parseAll(values: Collection<String>?): Set<Toolset> =
            values.orEmpty().mapNotNull { parse(it) }.toSet().ifEmpty { setOf(CORE, ASSISTANT, BROWSER) }
    }
}
