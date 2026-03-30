package com.example.securechat.domain.model

data class User(
    val id: String,
    val username: String,
    val email: String,
    val token: String? = null,
    val photoUrl: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val isPresenceHidden: Boolean = false
)
