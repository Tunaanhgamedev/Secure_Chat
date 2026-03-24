package com.example.securechat.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.securechat.domain.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("secure_chat_prefs", Context.MODE_PRIVATE)

    fun saveUserSession(user: User) {
        prefs.edit().apply {
            putString("USER_ID", user.id)
            putString("USERNAME", user.username)
            putString("EMAIL", user.email)
            putString("TOKEN", user.token)
            apply()
        }
    }

    fun getUserSession(): User? {
        val id = prefs.getString("USER_ID", null) ?: return null
        val username = prefs.getString("USERNAME", "") ?: ""
        val email = prefs.getString("EMAIL", "") ?: ""
        val token = prefs.getString("TOKEN", null)
        return User(id = id, username = username, email = email, token = token)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
