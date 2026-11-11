package com.aura.aura_ui.data.skills

import android.content.Context
import com.aura.aura_ui.data.db.SkillDao
import com.aura.aura_ui.data.db.UserSkillEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class Skill(
    val id: String,
    val name: String,
    val version: Int,
    val trigger: String,
    val steps: List<String>,
    val tools: List<String>,
    val guardrails: List<String>,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
)

/**
 * Skill packs are prompt-side procedures. Built-ins ship in assets/skills; user packs live in
 * filesDir/skills and the Room inventory so they can be toggled and deleted.
 */
@Singleton
class SkillsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: SkillDao,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var seeded = false

    fun observe(): Flow<List<Skill>> = dao.observeAll().map { rows -> rows.map { it.toSkill() } }

    suspend fun ensureSeeded() {
        if (seeded) return
        seeded = true
        val builtIns = loadBuiltIns()
        builtIns.forEach { skill ->
            if (dao.bySkillId(skill.id) == null) {
                dao.upsert(skill.toEntity(builtIn = true))
            }
        }
        // Filesystem packs override the DB copy on first sight.
        userPackDirs().forEach { dir ->
            val file = java.io.File(dir, "skill.json")
            if (file.exists()) {
                runCatching { parseSkill(file.readText()) }.getOrNull()?.let { skill ->
                    if (dao.bySkillId(skill.id) == null) dao.upsert(skill.toEntity(builtIn = false))
                }
            }
        }
    }

    suspend fun enabled(): List<Skill> {
        ensureSeeded()
        return dao.enabled().map { it.toSkill() }
    }

    suspend fun all(): List<Skill> {
        ensureSeeded()
        return dao.all().map { it.toSkill() }
    }

    suspend fun setEnabled(skillId: String, enabled: Boolean) {
        val existing = dao.bySkillId(skillId) ?: return
        dao.upsert(existing.copy(enabled = enabled))
    }

    suspend fun delete(skillId: String) {
        dao.delete(skillId)
    }

    suspend fun import(raw: String): Boolean {
        val skill = runCatching { parseSkill(raw) }.getOrNull() ?: return false
        dao.upsert(skill.toEntity(builtIn = false))
        return true
    }

    fun promptBlock(skills: List<Skill>): String {
        if (skills.isEmpty()) return ""
        return buildString {
            appendLine("Procedures you may follow (only when relevant):")
            skills.forEach { s ->
                appendLine("- ${s.name}: ${s.steps.joinToString(" → ")}")
            }
        }
    }

    private fun loadBuiltIns(): List<Skill> {
        val index = runCatching {
            context.assets.open("skills/index.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return defaultSkills
        return runCatching {
            val root = json.parseToJsonElement(index).jsonObject
            (root["skills"] as? JsonArray).orEmpty().mapNotNull { element ->
                runCatching { parseSkill(element.jsonObject.toString()) }.getOrNull()
            }
        }.getOrDefault(defaultSkills).ifEmpty { defaultSkills }
    }

    private fun parseSkill(raw: String): Skill {
        val o = json.parseToJsonElement(raw).jsonObject
        fun strings(key: String): List<String> =
            (o[key] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
        return Skill(
            id = o["id"]?.jsonPrimitive?.content ?: error("missing id"),
            name = o["name"]?.jsonPrimitive?.content ?: "Untitled skill",
            version = o["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
            trigger = o["trigger"]?.jsonPrimitive?.content ?: "",
            steps = strings("steps"),
            tools = strings("tools"),
            guardrails = strings("guardrails"),
        )
    }

    private fun userPackDirs(): List<java.io.File> =
        java.io.File(context.filesDir, "skills").listFiles()?.filter { it.isDirectory }.orEmpty()

    private fun Skill.toEntity(builtIn: Boolean) = UserSkillEntity(
        skillId = id,
        name = name,
        version = version,
        trigger = trigger,
        stepsJson = json.encodeToString(JsonArray.serializer(), JsonArray(steps.map { kotlinx.serialization.json.JsonPrimitive(it) })),
        toolsJson = json.encodeToString(JsonArray.serializer(), JsonArray(tools.map { kotlinx.serialization.json.JsonPrimitive(it) })),
        guardrailsJson = json.encodeToString(JsonArray.serializer(), JsonArray(guardrails.map { kotlinx.serialization.json.JsonPrimitive(it) })),
        enabled = enabled,
        builtIn = builtIn,
    )

    private fun UserSkillEntity.toSkill(): Skill {
        fun parse(value: String): List<String> = runCatching {
            (json.parseToJsonElement(value) as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
        }.getOrDefault(emptyList())
        return Skill(
            id = skillId,
            name = name,
            version = version,
            trigger = trigger,
            steps = parse(stepsJson),
            tools = parse(toolsJson),
            guardrails = parse(guardrailsJson),
            enabled = enabled,
            builtIn = builtIn,
        )
    }

    companion object {
        val defaultSkills = listOf(
            Skill(
                id = "send_photo",
                name = "Send a photo",
                version = 1,
                trigger = "user asks to share a picture",
                steps = listOf("open Photos", "long-press target", "tap Share", "choose app", "pick contact", "send"),
                tools = listOf("launch_app", "read_screen", "tap", "type_text", "verify_action"),
                guardrails = listOf("confirm before send"),
                builtIn = true,
            ),
            Skill(
                id = "navigate_home",
                name = "Navigate home",
                version = 1,
                trigger = "user asks for directions home",
                steps = listOf("open Maps", "tap search", "type home address", "select first result", "start navigation"),
                tools = listOf("launch_app", "set_value", "tap", "verify_action"),
                guardrails = listOf("never start navigation while driving is detected"),
                builtIn = true,
            ),
            Skill(
                id = "quiet_phone",
                name = "Quiet the phone",
                version = 1,
                trigger = "user asks to silence the phone",
                steps = listOf("turn on Do Not Disturb", "set media volume low", "confirm status"),
                tools = listOf("system_intent", "volume", "get_device_status"),
                guardrails = listOf("reversible actions only"),
                builtIn = true,
            ),
        )
    }
}
