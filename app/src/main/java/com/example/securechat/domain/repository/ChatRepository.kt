package com.example.securechat.domain.repository

import com.example.securechat.domain.model.Conversation
import com.example.securechat.domain.model.Message
import com.example.securechat.domain.model.User
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getUsers(): Flow<List<User>>
    fun getFriends(): Flow<List<User>>
    fun getConversations(): Flow<List<Conversation>>
    fun getMessages(otherUserId: String): Flow<List<Message>>
    suspend fun sendMessage(otherUserId: String, content: String)
    fun getGroupMessages(): Flow<List<Message>>
    suspend fun sendGroupMessage(content: String)
    
    // Pro Features (Version 2.0)
    fun getFriendRequests(): Flow<List<User>>
    suspend fun sendFriendRequest(targetUserId: String)
    suspend fun acceptFriendRequest(senderUserId: String)
    suspend fun rejectFriendRequest(senderUserId: String)
    fun isFriend(userId: String): Flow<Boolean>
    
    fun getMessageRequests(): Flow<List<Conversation>>

    // Call Signaling
    fun listenForIncomingCall(): Flow<com.example.securechat.domain.model.IncomingCallModel?>
    suspend fun startCall(targetUserId: String, callerName: String, callerPhotoUrl: String?): Result<Unit>
    suspend fun respondToCall(callerId: String, status: String): Result<Unit>
    fun listenForCallStatus(targetUserId: String): Flow<String?>
    suspend fun endCallSignal(targetUserId: String): Result<Unit>
    
    // Deletion
    suspend fun deleteMessageForMe(chatId: String, messageId: String, isGroup: Boolean = false): Result<Unit>
    suspend fun deleteMessageForEveryone(chatId: String, messageId: String, isGroup: Boolean = false): Result<Unit>
}
