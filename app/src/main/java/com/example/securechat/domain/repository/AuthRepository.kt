package com.example.securechat.domain.repository

import com.example.securechat.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<User>
    suspend fun register(username: String, email: String, password: String): Result<User>
    fun getCachedUser(): Flow<User?>
    suspend fun logout()
}
