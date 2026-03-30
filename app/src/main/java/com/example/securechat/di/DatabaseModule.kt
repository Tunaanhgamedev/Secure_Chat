package com.example.securechat.di

import android.content.Context
import androidx.room.Room
import com.example.securechat.data.local.db.SecureChatDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SecureChatDatabase {
        return Room.databaseBuilder(
            context,
            SecureChatDatabase::class.java,
            SecureChatDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: SecureChatDatabase) = database.messageDao
}
