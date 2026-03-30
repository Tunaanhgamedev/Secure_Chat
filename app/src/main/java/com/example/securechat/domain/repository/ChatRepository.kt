package com.example.securechat.domain.repository

import com.example.securechat.domain.model.Conversation
import com.example.securechat.domain.model.Message
import com.example.securechat.domain.model.User
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getUsers(): Flow<List<User>>
    fun getConversations(): Flow<List<Conversation>>
    fun getMessages(otherUserId: String): Flow<List<Message>>
    suspend fun sendMessage(otherUserId: String, content: String)
    fun getGroupMessages(): Flow<List<Message>>
    suspend fun sendGroupMessage(content: String)
}
