package com.example.securechat.domain.repository

import com.example.securechat.domain.model.Conversation
import com.example.securechat.domain.model.CustomGroup
import kotlinx.coroutines.flow.Flow

interface CustomGroupRepository {
    suspend fun createGroup(name: String, type: String, memberIds: List<String>): Result<String>
    fun getMyGroups(): Flow<List<CustomGroup>>
    fun getGroupInfo(groupId: String): Flow<CustomGroup?>
    fun getMyGroupConversations(): Flow<List<Conversation>>
    
    suspend fun addMemberRequest(groupId: String, userId: String): Result<Unit>
    suspend fun approveMember(groupId: String, userId: String): Result<Unit>
    suspend fun removeMemberOrRequest(groupId: String, userId: String): Result<Unit>
    suspend fun leaveGroup(groupId: String): Result<Unit>
    
    fun getCustomGroupMessages(groupId: String): Flow<List<com.example.securechat.domain.model.Message>>
    suspend fun sendCustomGroupMessage(groupId: String, content: String, fileUrl: String? = null, fileName: String? = null, fileType: String? = null): Result<Unit>
    
    // Deletion
    suspend fun deleteMessageForMe(groupId: String, messageId: String): Result<Unit>
    suspend fun deleteMessageForEveryone(groupId: String, messageId: String): Result<Unit>
}
