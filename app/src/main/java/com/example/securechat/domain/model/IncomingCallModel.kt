package com.example.securechat.domain.model

data class IncomingCallModel(
    val callerId: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String? = null,
    val status: String = "ringing", // "ringing", "accepted", "declined", "ended"
    val timestamp: Long = 0L
)
