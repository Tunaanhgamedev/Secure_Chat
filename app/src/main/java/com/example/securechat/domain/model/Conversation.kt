package com.example.securechat.domain.model

data class Conversation(
    val peerId: String,
    val peerName: String,
    val peerEmail: String,
    val lastMessage: String,
    val lastTimestamp: Long
)
