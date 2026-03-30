package com.example.securechat.domain.repository

import com.example.securechat.domain.model.User
import kotlinx.coroutines.flow.Flow
import android.net.Uri

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(username: String, email: String, password: String): Result<User>
    fun getCachedUser(): Flow<User?>
    suspend fun logout()
    
    // Pro Features (Version 2.0)
    suspend fun reauthenticate(password: String): Result<Unit>
    suspend fun updateProfile(username: String, photoUrl: String?): Result<Unit>
    suspend fun updateEmail(newEmail: String): Result<Unit>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    suspend fun uploadAvatar(uri: Uri): Result<String>
    suspend fun updatePresence(isOnline: Boolean, isHidden: Boolean? = null): Result<Unit>
}
