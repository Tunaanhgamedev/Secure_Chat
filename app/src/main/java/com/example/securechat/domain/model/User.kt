package com.example.securechat.domain.model

data class User(
    val id: String,
    val username: String,
    val email: String,
    val token: String? = null
)
