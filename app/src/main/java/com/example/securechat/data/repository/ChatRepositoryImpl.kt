package com.example.securechat.data.repository

import com.example.securechat.domain.model.Message
import com.example.securechat.domain.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor() : ChatRepository {

    private val messages = MutableStateFlow<List<Message>>(emptyList())
    private var isConnected = false

    override fun connect() {
        isConnected = true
        // Mock some initial messages
        messages.value = listOf(
            Message("1", "system", "System", "Welcome to SecureChat!", System.currentTimeMillis() - 10000, false)
        )
    }

    override fun disconnect() {
        isConnected = false
        messages.value = emptyList()
    }

    override fun getMessages(): Flow<List<Message>> {
        return messages
    }

    override suspend fun sendMessage(content: String) {
        if (!isConnected) return
        
        val newMsg = Message(
            id = System.currentTimeMillis().toString(),
            senderId = "me", // Replace with real user ID
            senderName = "Tôi", // Me
            content = content,
            timestamp = System.currentTimeMillis(),
            isMine = true
        )
        
        messages.value = messages.value + newMsg
        
        // Mock a bot reply after 1 sec
        delay(1000)
        val reply = Message(
            id = (System.currentTimeMillis() + 1).toString(),
            senderId = "bot",
            senderName = "SecureBot",
            content = "Received: $content",
            timestamp = System.currentTimeMillis(),
            isMine = false
        )
        messages.value = messages.value + reply
    }
}
