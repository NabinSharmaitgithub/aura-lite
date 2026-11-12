package com.aura.aura_ui.data.eval

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Persists eval verdict records for the debug cockpit/harness. */
@Singleton
class EvalVerdictRecorder @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "eval/verdicts.json").also { it.parentFile?.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Synchronized
    fun append(record: EvalVerdictRecord) {
        val existing = readAll().toMutableList()
        existing.add(record)
        val arr = buildJsonArray {
            existing.takeLast(200).forEach { r ->
                add(
                    buildJsonObject {
                        put("taskId", r.taskId)
                        put("verdict", r.verdict.name)
                        put("steps", r.steps)
                        put("detail", r.detail ?: "")
                        put("at", r.at)
                    },
                )
            }
        }
        runCatching { file.writeText(json.encodeToString(JsonArray.serializer(), arr)) }
    }

    @Synchronized
    fun readAll(): List<EvalVerdictRecord> = runCatching {
        if (!file.exists()) return emptyList()
        val root = json.parseToJsonElement(file.readText()).jsonArray
        root.mapNotNull { el ->
            val o = el.jsonObject
            val taskId = (o["taskId"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val verdict = runCatching { EvalVerdict.valueOf((o["verdict"] as? JsonPrimitive)?.content ?: "INVALID") }
                .getOrDefault(EvalVerdict.INVALID)
            EvalVerdictRecord(
                taskId = taskId,
                verdict = verdict,
                steps = (o["steps"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                detail = (o["detail"] as? JsonPrimitive)?.content?.ifBlank { null },
                at = (o["at"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
            )
        }
    }.getOrDefault(emptyList())

    fun clear() { runCatching { file.delete() } }
}
