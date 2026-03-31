package com.example.securechat.domain.model

data class Conversation(
    val peerId: String,
    val peerName: String,
    val peerEmail: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val peerPhotoUrl: String? = null,
    val isGroup: Boolean = false,
    val groupId: String? = null
)
