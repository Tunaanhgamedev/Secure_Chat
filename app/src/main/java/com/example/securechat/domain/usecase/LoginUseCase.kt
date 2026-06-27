package com.example.securechat.domain.usecase

import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        if (email.isBlank() || password.isBlank()) {
            return Result.failure(Exception("Email và mật khẩu không được để trống."))
        }
        return repository.login(email, password)
    }
}
