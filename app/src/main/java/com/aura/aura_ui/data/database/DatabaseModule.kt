package com.aura.aura_ui.data.database

import android.content.Context
import androidx.room.Room
import com.aura.aura_ui.data.database.dao.AuraContactDao
import com.aura.aura_ui.data.database.dao.SttCorrectionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ContactDatabaseModule {

    @Provides
    @Singleton
    fun provideContactDatabase(@ApplicationContext context: Context): AuraDatabase =
        Room.databaseBuilder(context, AuraDatabase::class.java, "aura_contacts.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideContactDao(db: AuraDatabase): AuraContactDao = db.contactDao()

    @Provides
    fun provideSttCorrectionDao(db: AuraDatabase): SttCorrectionDao = db.sttCorrectionDao()

    @Provides
    fun provideAudioDao(db: AuraDatabase): AudioDao = db.audioDao()
}