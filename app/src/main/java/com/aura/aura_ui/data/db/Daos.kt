package com.aura.aura_ui.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AuraContactDao {
    @Upsert
    suspend fun upsert(contact: AuraContactEntity)

    @Upsert
    suspend fun upsertAll(contacts: List<AuraContactEntity>)

    @Query("SELECT * FROM aura_contacts ORDER BY displayName")
    fun observeAll(): Flow<List<AuraContactEntity>>

    @Query("SELECT * FROM aura_contacts ORDER BY displayName")
    suspend fun all(): List<AuraContactEntity>

    @Query("SELECT * FROM aura_contacts WHERE displayName LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchByName(query: String, limit: Int): List<AuraContactEntity>

    @Query(
        "SELECT * FROM aura_contacts WHERE phoneticKey = :key OR phoneticKeyAlt = :key LIMIT :limit",
    )
    suspend fun searchByPhonetic(key: String, limit: Int): List<AuraContactEntity>

    @Query("SELECT * FROM aura_contacts WHERE contactId = :contactId")
    suspend fun byId(contactId: String): AuraContactEntity?

    @Query("DELETE FROM aura_contacts")
    suspend fun clear()

    @Query("DELETE FROM aura_contacts WHERE contactId = :contactId")
    suspend fun delete(contactId: String)
}

@Dao
interface SttCorrectionDao {
    @Query("SELECT * FROM stt_corrections WHERE LOWER(heard) = LOWER(:heard) LIMIT 1")
    suspend fun find(heard: String): SttCorrectionEntity?

    @Query("SELECT * FROM stt_corrections ORDER BY count DESC LIMIT :limit")
    suspend fun top(limit: Int): List<SttCorrectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(correction: SttCorrectionEntity): Long

    @Query("DELETE FROM stt_corrections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM stt_corrections")
    suspend fun clear()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE fired = 0 ORDER BY triggerAt")
    fun observePending(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE fired = 0 AND triggerAt <= :now ORDER BY triggerAt")
    suspend fun due(now: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY triggerAt")
    suspend fun all(): List<ReminderEntity>

    @Query("DELETE FROM reminders")
    suspend fun clear()

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET fired = 1 WHERE id = :id")
    suspend fun markFired(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY name")
    fun observeAll(): Flow<List<UserSkillEntity>>

    @Query("SELECT * FROM skills WHERE enabled = 1 ORDER BY name")
    suspend fun enabled(): List<UserSkillEntity>

    @Query("SELECT * FROM skills ORDER BY name")
    suspend fun all(): List<UserSkillEntity>

    @Query("SELECT * FROM skills WHERE skillId = :skillId LIMIT 1")
    suspend fun bySkillId(skillId: String): UserSkillEntity?

    @Upsert
    suspend fun upsert(skill: UserSkillEntity): Long

    @Query("DELETE FROM skills")
    suspend fun clear()

    @Query("DELETE FROM skills WHERE skillId = :skillId")
    suspend fun delete(skillId: String)
}
