package com.example.securechat.domain.model

data class CustomGroup(
    val id: String = "",
    val name: String = "",
    val type: String = "public", // "public" or "private"
    val adminId: String = "",
    val members: Map<String, Boolean> = emptyMap(),
    val pendingRequests: Map<String, Boolean> = emptyMap(),
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L
)
