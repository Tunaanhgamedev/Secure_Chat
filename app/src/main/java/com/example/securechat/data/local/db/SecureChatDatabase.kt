package com.example.securechat.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.securechat.data.local.dao.MessageDao
import com.example.securechat.data.local.entity.MessageEntity

@Database(entities = [MessageEntity::class], version = 3, exportSchema = false)
abstract class SecureChatDatabase : RoomDatabase() {
    abstract val messageDao: MessageDao

    companion object {
        const val DATABASE_NAME = "secure_chat_db"
        
        fun create(context: android.content.Context): SecureChatDatabase {
            return androidx.room.Room.databaseBuilder(
                context,
                SecureChatDatabase::class.java,
                DATABASE_NAME
            ).fallbackToDestructiveMigration()
             .build()
        }
    }
}
