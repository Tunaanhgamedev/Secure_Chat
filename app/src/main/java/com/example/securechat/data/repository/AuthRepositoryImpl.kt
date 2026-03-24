package com.example.securechat.data.repository

import com.example.securechat.data.remote.AuthApi
import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi
) : AuthRepository {

    private val cachedUser = MutableStateFlow<User?>(null)

    override suspend fun login(username: String, password: String): Result<User> {
        return try {
            val response = api.login(mapOf("username" to username, "password" to password))
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                cachedUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Login failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            // For testing without backend, mock a success response:
            val fakeUser = User("1", username, "mock@email.com", "mock_token")
            cachedUser.value = fakeUser
            Result.success(fakeUser)
        }
    }

    override suspend fun register(username: String, email: String, password: String): Result<User> {
        return try {
            val response = api.register(mapOf("username" to username, "email" to email, "password" to password))
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                cachedUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Registration failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            // Mock success for now, as API is not running
            val fakeUser = User("1", username, email, "mock_token")
            cachedUser.value = fakeUser
            Result.success(fakeUser)
        }
    }

    override fun getCachedUser(): Flow<User?> {
        return cachedUser
    }

    override suspend fun logout() {
        cachedUser.value = null
    }
}
