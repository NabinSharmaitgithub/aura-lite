package com.aura.aura_ui.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        AuraContactEntity::class,
        SttCorrectionEntity::class,
        ReminderEntity::class,
        UserSkillEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun contactDao(): AuraContactDao
    abstract fun sttCorrectionDao(): SttCorrectionDao
    abstract fun reminderDao(): ReminderDao
    abstract fun skillDao(): SkillDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AuraDatabase =
        Room.databaseBuilder(context, AuraDatabase::class.java, "aura.db")
            // Never fall back destructively in a build that holds user learnings.
            .build()

    @Provides fun contactDao(db: AuraDatabase): AuraContactDao = db.contactDao()
    @Provides fun sttCorrectionDao(db: AuraDatabase): SttCorrectionDao = db.sttCorrectionDao()
    @Provides fun reminderDao(db: AuraDatabase): ReminderDao = db.reminderDao()
    @Provides fun skillDao(db: AuraDatabase): SkillDao = db.skillDao()
}
