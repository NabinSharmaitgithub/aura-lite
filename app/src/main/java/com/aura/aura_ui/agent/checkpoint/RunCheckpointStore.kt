package com.aura.aura_ui.agent.checkpoint

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.aura.aura_ui.state.CheckpointAvailability
import kotlinx.serialization.json.Json

/** Atomic checkpoint persistence (temp file + rename) for resumable runs. */
@Singleton
class RunCheckpointStore @Inject constructor(@ApplicationContext context: Context) {

    private val dir = File(context.filesDir, "checkpoints").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    fun write(checkpoint: RunCheckpoint): Boolean = runCatching {
        val target = File(dir, "${checkpoint.sessionId}.json")
        val temp = File(dir, "${checkpoint.sessionId}.json.tmp")
        temp.writeText(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), checkpoint.toJson()))
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        true
    }.getOrDefault(false)

    fun read(sessionId: String): RunCheckpoint? = runCatching {
        val file = File(dir, "$sessionId.json")
        if (!file.exists()) return null
        json.parseToJsonElement(file.readText()).let { RunCheckpoint.fromJson(it as kotlinx.serialization.json.JsonObject) }
    }.getOrNull()

    fun latest(): RunCheckpoint? = runCatching {
        dir.listFiles()?.filter { it.name.endsWith(".json") }
            ?.maxByOrNull { it.lastModified() }
            ?.let { file -> json.parseToJsonElement(file.readText()).let { RunCheckpoint.fromJson(it as kotlinx.serialization.json.JsonObject) } }
    }.getOrNull()

    fun clear(sessionId: String) {
        runCatching { File(dir, "$sessionId.json").delete() }
    }

    /** Is there a usable checkpoint to resume from? */
    fun availability(): CheckpointAvailability = runCatching {
        val c = latest() ?: return@runCatching CheckpointAvailability.None
        CheckpointAvailability.Available(c.sessionId, c.goal, c.stepCount)
    }.getOrDefault(CheckpointAvailability.None)
}
