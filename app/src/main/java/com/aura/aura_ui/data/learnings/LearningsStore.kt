package com.aura.aura_ui.data.learnings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class LearningFact(
    val id: String,
    val scope: String,
    val text: String,
    val confidence: Float,
    val sources: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Durable, user-editable preferences. Only facts with confidence >= 0.5 are injected into a
 * prompt, and app-scoped facts are injected only while that app is foreground.
 *
 * Facts and voice corrections share one JSON document, so every write re-serialises both
 * sections; dropping one of them on save would silently lose user data.
 */
@Singleton
class LearningsStore @Inject constructor(@ApplicationContext context: Context) {

    private val file = File(context.filesDir, "learnings/learnings.json")
    private val tombstones = mutableSetOf<String>()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        file.parentFile?.mkdirs()
    }

    @Synchronized
    fun facts(): List<LearningFact> {
        val root = readRoot() ?: return emptyList()
        return runCatching {
            (root["facts"] as? JsonArray).orEmpty().mapNotNull { element ->
                val o = element.jsonObject
                LearningFact(
                    id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    scope = o["scope"]?.jsonPrimitive?.content ?: "global",
                    text = o["text"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    confidence = o["confidence"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.5f,
                    sources = (o["sources"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty(),
                    createdAt = o["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                )
            }.filterNot { it.id in tombstones }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(fact: LearningFact) {
        if (fact.id in tombstones) return
        write(facts().filterNot { it.id == fact.id } + fact)
    }

    @Synchronized
    fun delete(id: String) {
        tombstones += id
        write(facts().filterNot { it.id == id })
    }

    @Synchronized
    fun clear(scope: String? = null) {
        if (scope == null) {
            facts().forEach { tombstones += it.id }
            write(emptyList())
        } else {
            facts().filter { it.scope == scope }.forEach { tombstones += it.id }
            write(facts().filterNot { it.scope == scope })
        }
    }

    fun promptBlock(packageName: String?): String {
        val usable = facts()
            .filter { it.confidence >= 0.5f }
            .filter { it.scope == "global" || (packageName != null && it.scope == packageName) }
        if (usable.isEmpty()) return ""
        return buildString {
            appendLine("What you have learned about this user (respect these):")
            usable.forEach { appendLine("- ${it.text}") }
        }
    }

    @Synchronized
    fun corrections(): List<Pair<String, String>> {
        val root = readRoot() ?: return emptyList()
        return runCatching {
            (root["corrections"] as? JsonArray).orEmpty().mapNotNull {
                val o = it.jsonObject
                val heard = o["heard"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val corrected = o["corrected"]?.jsonPrimitive?.content ?: return@mapNotNull null
                heard to corrected
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun recordCorrection(heard: String, corrected: String) {
        if (heard.isBlank()) return
        val merged = corrections().toMutableList()
        val existing = merged.indexOfFirst { it.first.equals(heard, ignoreCase = true) }
        if (existing >= 0) merged[existing] = heard to corrected else merged += heard to corrected
        write(facts(), merged)
    }

    /** Removes every voice correction while leaving learned facts untouched. */
    @Synchronized
    fun clearCorrections() {
        write(facts(), emptyList())
    }

    private fun readRoot(): JsonObject? {
        if (!file.exists()) return null
        return runCatching {
            json.parseToJsonElement(file.readText()).jsonObject
        }.getOrNull()
    }

    private fun write(
        facts: List<LearningFact>,
        corrections: List<Pair<String, String>> = corrections(),
    ) {
        val root = buildJsonObject {
            put("version", 1)
            put("updatedAt", System.currentTimeMillis())
            put(
                "facts",
                buildJsonArray {
                    facts.forEach { fact ->
                        add(
                            buildJsonObject {
                                put("id", fact.id)
                                put("scope", fact.scope)
                                put("text", fact.text)
                                put("confidence", fact.confidence)
                                put("createdAt", fact.createdAt)
                                put("sources", buildJsonArray { fact.sources.forEach { add(it) } })
                            },
                        )
                    }
                },
            )
            put(
                "corrections",
                buildJsonArray {
                    corrections.forEach { (heard, corrected) ->
                        add(
                            buildJsonObject {
                                put("heard", heard)
                                put("corrected", corrected)
                            },
                        )
                    }
                },
            )
        }
        runCatching { file.writeText(json.encodeToString(JsonObject.serializer(), root)) }
    }
}
