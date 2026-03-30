package com.example.securechat.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.securechat.data.local.dao.MessageDao
import com.example.securechat.data.local.entity.MessageEntity

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class SecureChatDatabase : RoomDatabase() {
    abstract val messageDao: MessageDao

    companion object {
        const val DATABASE_NAME = "secure_chat_db"
    }
}
