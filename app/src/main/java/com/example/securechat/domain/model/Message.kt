package com.example.securechat.domain.model

data class Message(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val isMine: Boolean,
    val isDeletedForEveryone: Boolean = false,
    val deletedForUsers: Map<String, Boolean> = emptyMap(),
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileType: String? = null
)
