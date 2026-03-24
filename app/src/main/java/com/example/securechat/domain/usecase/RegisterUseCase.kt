package com.example.securechat.domain.usecase

import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(username: String, email: String, password: String): Result<User> {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            return Result.failure(Exception("All fields are required."))
        }
        if (password.length < 6) {
            return Result.failure(Exception("Password must be at least 6 characters."))
        }
        return repository.register(username, email, password)
    }
}
