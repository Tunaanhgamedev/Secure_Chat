package com.example.securechat.data.local.entity

import androidx.room.*

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val isMine: Boolean,
    val chatId: String,
    val isDeletedForEveryone: Boolean = false,
    val deletedByUsers: String = "" // Comma-separated UIDs
)
