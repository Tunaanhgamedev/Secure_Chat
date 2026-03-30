package com.example.securechat.domain.repository

import com.example.securechat.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun connect()
    fun disconnect()
    fun getMessages(): Flow<List<Message>>
    suspend fun sendMessage(content: String)
}
