package com.aura.aura_ui.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Entity(
    tableName = "aura_contacts",
    indices = [Index("phoneticKey"), Index("phoneticKeyAlt")],
)
data class AuraContactEntity(
    @PrimaryKey val contactId: String,
    val displayName: String,
    val phoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val photoUri: String? = null,
    val phoneticKey: String = "",
    val phoneticKeyAlt: String = "",
    val aliases: List<String> = emptyList(),
    val lastSyncedAt: Long = 0L,
)

@Entity(tableName = "stt_corrections")
data class SttCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val heard: String,
    val corrected: String,
    val count: Int = 1,
    val lastSeenAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reminders", indices = [Index("triggerAt")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val triggerAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val fired: Boolean = false,
    val sessionId: String? = null,
)

@Entity(tableName = "skills", indices = [Index(value = ["skillId"], unique = true)])
data class UserSkillEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val skillId: String,
    val name: String,
    val version: Int = 1,
    val trigger: String = "",
    val stepsJson: String = "[]",
    val toolsJson: String = "[]",
    val guardrailsJson: String = "[]",
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun stringListToJson(value: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(String.serializer()), value) }
            .getOrDefault(emptyList())
}
