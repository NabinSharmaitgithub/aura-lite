package com.aura.aura_ui.firebase

import android.content.Context
import com.aura.aura_ui.data.db.ReminderDao
import com.aura.aura_ui.data.db.SkillDao
import com.aura.aura_ui.data.db.ReminderEntity
import com.aura.aura_ui.data.db.UserSkillEntity
import com.aura.aura_ui.data.learnings.LearningsStore
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.data.session.SessionLogStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Opt-in Firestore sync. Every snapshot is a single document under `users/{uid}/snapshots/{kind}`
 * whose `payload` holds the JSON document/array; Firestore rules enforce ownership and shape.
 *
 * Facts, plans and skills are two-way with last-write-wins by the snapshot's `updatedAt`.
 * Session history is push-only (a device never imports fabricated history from the cloud) and
 * uploads only if the user turned on "Session sync". Nothing is uploaded unless the user
 * linked an account and switched sync on.
 */
@Singleton
class FirebaseSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val learnings: LearningsStore,
    private val reminderDao: ReminderDao,
    private val skillDao: SkillDao,
    private val sessions: SessionLogStore,
    private val prefs: AuraSettings,
) {
    data class Report(
        val pushed: List<String>,
        val pulled: List<String>,
        val skipped: List<String>,
        val errors: List<String>,
        val detail: String,
        val ok: Boolean,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    suspend fun syncNow(): Report {
        val unsynced = listOf("facts", "plans", "skills", "sessions")
        if (!FirebaseOptional.available(context)) {
            return Report(emptyList(), emptyList(), unsynced, emptyList(), "Firebase is not configured on this build", ok = false)
        }
        if (!prefs.firebaseEnabled) {
            return Report(emptyList(), emptyList(), unsynced, emptyList(), "Link a Firebase account first", ok = false)
        }
        val db = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return Report(emptyList(), emptyList(), unsynced, emptyList(), "Firestore unavailable", ok = false)
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
            ?: return Report(emptyList(), emptyList(), unsynced, emptyList(), "Sign in to sync", ok = false)

        val pushed = mutableListOf<String>()
        val pulled = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val errors = mutableListOf<String>()
        fun record(kind: String, outcome: String) {
            when (outcome) {
                "pushed" -> pushed += kind
                "pulled" -> pulled += kind
                else -> skipped += kind
            }
        }

        runCatching { syncSnapshot(db, user.uid, "facts", ::readFacts, ::writeFacts) }
            .onSuccess { record("facts", it) }
            .onFailure { errors += "facts: ${it.message}" }
        runCatching { syncSnapshot(db, user.uid, "plans", ::readPlans, ::writePlans) }
            .onSuccess { record("plans", it) }
            .onFailure { errors += "plans: ${it.message}" }
        runCatching { syncSnapshot(db, user.uid, "skills", ::readSkills, ::writeSkills) }
            .onSuccess { record("skills", it) }
            .onFailure { errors += "skills: ${it.message}" }

        if (prefs.firebaseSessionSyncEnabled) {
            runCatching { pushSessions(db, user.uid) }
                .onSuccess { pushed += "sessions" }
                .onFailure { errors += "sessions: ${it.message}" }
        } else {
            skipped += "sessions (off)"
        }

        val detail = buildString {
            if (pushed.isNotEmpty()) append("Pushed " + pushed.joinToString(", "))
            if (pushed.isNotEmpty() && pulled.isNotEmpty()) append(" · ")
            if (pulled.isNotEmpty()) append("Pulled " + pulled.joinToString(", "))
            if (isNotEmpty()) append(". ")
            if (skipped.isNotEmpty()) append("Skipped " + skipped.joinToString(", ") + ".")
            if (errors.isNotEmpty()) append(" Failed: " + errors.joinToString(", ") + ".")
        }.ifBlank { "Nothing to sync" }
        return Report(pushed, pulled, skipped, errors, detail, ok = errors.isEmpty())
    }


    private suspend fun syncSnapshot(
        db: FirebaseFirestore,
        uid: String,
        kind: String,
        readLocal: suspend () -> Pair<String, Long>?,
        writeLocal: suspend (String, Long) -> Unit,
    ): String {
        val snapshots = db.collection("users").document(uid).collection("snapshots")
        val local = readLocal()
        val docRef = snapshots.document(kind)
        val remote = docRef.get().await()
        val remoteTs = if (remote.exists()) remote.getLong("updatedAt") ?: 0L else 0L
        if (local == null && remoteTs == 0L) return "skipped"
        val localTs = local?.second ?: 0L
        return if (remoteTs > localTs) {
            val payload = remote.getString("payload") ?: return "skipped"
            writeLocal(payload, remoteTs)
            prefs.setLastSyncAt(kind, remoteTs)
            "pulled"
        } else {
            val ts = if (localTs > 0L) localTs else System.currentTimeMillis()
            writeRemote(snapshots, kind, local!!.first, ts)
            prefs.setLastSyncAt(kind, ts)
            "pushed"
        }
    }

    private suspend fun writeRemote(
        snapshots: CollectionReference,
        kind: String,
        payload: String,
        updatedAt: Long,
    ) {
        snapshots.document(kind).set(
            mapOf(
                "payload" to payload,
                "updatedAt" to updatedAt,
                "deviceId" to prefs.deviceId(),
                "version" to FirebaseOptional.SNAPSHOT_VERSION,
            ),
        ).await()
    }

    private fun readFacts(): Pair<String, Long>? {
        val raw = learnings.exportRaw() ?: return null
        val ts = runCatching {
            json.parseToJsonElement(raw).jsonObject["updatedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
        return raw to ts
    }

    private fun writeFacts(payload: String, ignoredTs: Long) {
        learnings.importRaw(payload)
    }

    private suspend fun readPlans(): Pair<String, Long>? {
        val all = runCatching { reminderDao.all() }.getOrDefault(emptyList())
        if (all.isEmpty()) return null
        val payload = buildJsonArray {
            all.forEach { reminder ->
                add(
                    buildJsonObject {
                        put("text", reminder.text)
                        put("triggerAt", reminder.triggerAt)
                        put("createdAt", reminder.createdAt)
                        put("fired", reminder.fired)
                        reminder.sessionId?.let { put("sessionId", it) }
                    },
                )
            }
        }.toString()
        return payload to (all.maxOfOrNull { it.createdAt } ?: 0L)
    }

    private suspend fun writePlans(payload: String, ignoredTs: Long) {
        val items = runCatching { json.parseToJsonElement(payload) as? JsonArray }.getOrNull() ?: return
        reminderDao.clear()
        items.forEach { element ->
            val o = element.jsonObject
            reminderDao.insert(
                ReminderEntity(
                    text = o["text"]?.jsonPrimitive?.content ?: return@forEach,
                    triggerAt = o["triggerAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@forEach,
                    createdAt = o["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis(),
                    fired = o["fired"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                    sessionId = o["sessionId"]?.jsonPrimitive?.content,
                ),
            )
        }
    }

    private suspend fun readSkills(): Pair<String, Long>? {
        val all = runCatching { skillDao.all() }.getOrDefault(emptyList())
        if (all.isEmpty()) return null
        val payload = buildJsonArray {
            all.forEach { skill ->
                add(
                    buildJsonObject {
                        put("skillId", skill.skillId)
                        put("name", skill.name)
                        put("version", skill.version)
                        put("trigger", skill.trigger)
                        put("stepsJson", skill.stepsJson)
                        put("toolsJson", skill.toolsJson)
                        put("guardrailsJson", skill.guardrailsJson)
                        put("enabled", skill.enabled)
                        put("builtIn", skill.builtIn)
                        put("updatedAt", skill.updatedAt)
                    },
                )
            }
        }.toString()
        return payload to (all.maxOfOrNull { it.updatedAt } ?: 0L)
    }

    private suspend fun writeSkills(payload: String, ignoredTs: Long) {
        val items = runCatching { json.parseToJsonElement(payload) as? JsonArray }.getOrNull() ?: return
        skillDao.clear()
        items.forEach { element ->
            val o = element.jsonObject
            skillDao.upsert(
                UserSkillEntity(
                    skillId = o["skillId"]?.jsonPrimitive?.content ?: return@forEach,
                    name = o["name"]?.jsonPrimitive?.content ?: return@forEach,
                    version = o["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                    trigger = o["trigger"]?.jsonPrimitive?.content ?: "",
                    stepsJson = o["stepsJson"]?.jsonPrimitive?.content ?: "[]",
                    toolsJson = o["toolsJson"]?.jsonPrimitive?.content ?: "[]",
                    guardrailsJson = o["guardrailsJson"]?.jsonPrimitive?.content ?: "[]",
                    enabled = o["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                    builtIn = o["builtIn"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                    updatedAt = o["updatedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun pushSessions(db: FirebaseFirestore, uid: String) {
        val summaries = sessions.sessions(25)
        val auditLines = runCatching {
            File(context.filesDir, "mcp/audit.jsonl").readLines().filter { it.isNotBlank() }.takeLast(100)
        }.getOrDefault(emptyList())
        val payload = buildJsonObject {
            put("generatedAt", System.currentTimeMillis())
            put(
                "sessions",
                buildJsonArray {
                    summaries.forEach { s ->
                        add(
                            buildJsonObject {
                                put("id", s.id)
                                put("goal", s.goal)
                                put("startedAt", s.startedAt)
                                s.endedAt?.let { put("endedAt", it) }
                                put("outcome", s.outcome ?: "unknown")
                                put("steps", s.steps)
                                put("provider", s.provider)
                                put("model", s.model)
                            },
                        )
                    }
                },
            )
            put("audit", buildJsonArray { auditLines.forEach { add(it) } })
        }.toString()
        db.collection("users").document(uid).collection("snapshots").document("sessions")
            .set(
                mapOf(
                    "payload" to payload,
                    "updatedAt" to System.currentTimeMillis(),
                    "deviceId" to prefs.deviceId(),
                    "version" to FirebaseOptional.SNAPSHOT_VERSION,
                ),
            )
            .await()
    }
}
