package com.example.securechat.data.repository

import com.example.securechat.domain.model.Conversation
import com.example.securechat.domain.model.CustomGroup
import com.example.securechat.domain.model.Message
import com.example.securechat.domain.repository.CustomGroupRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomGroupRepositoryImpl @Inject constructor() : CustomGroupRepository {

    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val groupsRef = db.getReference("custom_groups")
    private val userGroupsRef = db.getReference("user_custom_groups")
    private val messagesRef = db.getReference("custom_group_messages")

    override suspend fun createGroup(name: String, type: String, memberIds: List<String>): Result<String> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val groupId = UUID.randomUUID().toString()
            
            val membersMap = mutableMapOf<String, Boolean>()
            membersMap[myUid] = true
            memberIds.forEach { membersMap[it] = true }

            val newGroup = CustomGroup(
                id = groupId,
                name = name,
                type = type,
                adminId = myUid,
                members = membersMap,
                lastMessage = "Nhóm được tạo",
                lastTimestamp = System.currentTimeMillis()
            )

            // Save group data
            groupsRef.child(groupId).setValue(newGroup).await()

            // Update user_custom_groups for all members
            membersMap.keys.forEach { userId ->
                userGroupsRef.child(userId).child(groupId).setValue(true).await()
            }

            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getMyGroups(): Flow<List<CustomGroup>> = callbackFlow {
        val myUid = auth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val groupIds = snapshot.children.mapNotNull { it.key }
                if (groupIds.isEmpty()) {
                    trySend(emptyList())
                    return
                }

                // Need to fetch details for each group
                // Since this might be complex with Flow, Firebase listeners on specific nodes are better
                // For simplicity, we can just fetch once each time userGroups changes
                db.getReference("custom_groups").get().addOnSuccessListener { groupsSnapshot ->
                    val groups = groupIds.mapNotNull { gid ->
                        groupsSnapshot.child(gid).getValue(CustomGroup::class.java)
                    }
                    trySend(groups.sortedByDescending { it.lastTimestamp })
                }.addOnFailureListener {
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (error.code == DatabaseError.PERMISSION_DENIED) close() 
                else close(error.toException())
            }
        }
        
        userGroupsRef.child(myUid).addValueEventListener(listener)
        awaitClose { userGroupsRef.child(myUid).removeEventListener(listener) }
    }

    override fun getGroupInfo(groupId: String): Flow<CustomGroup?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val group = snapshot.getValue(CustomGroup::class.java)
                trySend(group)
            }
            override fun onCancelled(error: DatabaseError) {
                if (error.code == DatabaseError.PERMISSION_DENIED) close() 
                else close(error.toException())
            }
        }
        groupsRef.child(groupId).addValueEventListener(listener)
        awaitClose { groupsRef.child(groupId).removeEventListener(listener) }
    }

    // This converts CustomGroup into a Conversation object for HomeScreen
    override fun getMyGroupConversations(): Flow<List<Conversation>> = callbackFlow {
        val myUid = auth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val groupIds = snapshot.children.mapNotNull { it.key }
                if (groupIds.isEmpty()) {
                    trySend(emptyList())
                    return
                }

                db.getReference("custom_groups").get().addOnSuccessListener { groupsSnapshot ->
                    val conversations = groupIds.mapNotNull { gid ->
                        val grp = groupsSnapshot.child(gid).getValue(CustomGroup::class.java) ?: return@mapNotNull null
                        Conversation(
                            peerId = grp.id,
                            peerName = grp.name,
                            peerEmail = if (grp.type == "public") "Nhóm Công khai" else "Nhóm Kín",
                            lastMessage = grp.lastMessage,
                            lastTimestamp = grp.lastTimestamp,
                            isOnline = false,
                            lastSeen = 0L,
                            peerPhotoUrl = null, // Group doesn't have an avatar in this iteration unless added
                            isGroup = true,
                            groupId = grp.id
                        )
                    }
                    trySend(conversations.sortedByDescending { it.lastTimestamp })
                }.addOnFailureListener {
                    trySend(emptyList())
                }
            }
            override fun onCancelled(error: DatabaseError) {
                if (error.code == DatabaseError.PERMISSION_DENIED) close() 
                else close(error.toException())
            }
        }
        // Actually, we need to listen to custom_groups changes too, but watching user groups is the entry.
        // For real-time last message update, a direct listener on groups is better.
        // We will do a generic value listener on the user's groups
        
        userGroupsRef.child(myUid).addValueEventListener(listener)
        awaitClose { userGroupsRef.child(myUid).removeEventListener(listener) }
    }

    override suspend fun addMemberRequest(groupId: String, userId: String): Result<Unit> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val groupSnap = groupsRef.child(groupId).get().await()
            val group = groupSnap.getValue(CustomGroup::class.java) ?: throw Exception("Group not found")

            if (group.type == "public" || myUid == group.adminId) {
                // Add directly
                groupsRef.child(groupId).child("members").child(userId).setValue(true).await()
                userGroupsRef.child(userId).child(groupId).setValue(true).await()
            } else {
                // Private and not admin -> pending request
                groupsRef.child(groupId).child("pendingRequests").child(userId).setValue(true).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun approveMember(groupId: String, userId: String): Result<Unit> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val groupSnap = groupsRef.child(groupId).get().await()
            val group = groupSnap.getValue(CustomGroup::class.java) ?: throw Exception("Group not found")

            if (myUid != group.adminId) throw Exception("Only admin can approve")

            // Remove from pending
            groupsRef.child(groupId).child("pendingRequests").child(userId).removeValue().await()
            // Add to members
            groupsRef.child(groupId).child("members").child(userId).setValue(true).await()
            userGroupsRef.child(userId).child(groupId).setValue(true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeMemberOrRequest(groupId: String, userId: String): Result<Unit> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val groupSnap = groupsRef.child(groupId).get().await()
            val group = groupSnap.getValue(CustomGroup::class.java) ?: throw Exception("Group not found")

            if (myUid != group.adminId && myUid != userId) throw Exception("Only admin can remove others")

            // Remove from pending
            groupsRef.child(groupId).child("pendingRequests").child(userId).removeValue().await()
            // Remove from members
            groupsRef.child(groupId).child("members").child(userId).removeValue().await()
            userGroupsRef.child(userId).child(groupId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveGroup(groupId: String): Result<Unit> {
        val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        return removeMemberOrRequest(groupId, myUid)
    }

    override fun getCustomGroupMessages(groupId: String): Flow<List<Message>> = callbackFlow {
        val myUid = auth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val msgs = snapshot.children.mapNotNull { child ->
                    val senderId = child.child("senderId").getValue(String::class.java) ?: return@mapNotNull null
                    val isDeleted = child.child("deletedForEveryone").getValue(Boolean::class.java) ?: false
                    val delMap = child.child("deletedForUsers").value as? Map<*, *>
                    
                    // Filter out if current user deleted this for themselves
                    if (delMap?.containsKey(myUid) == true) return@mapNotNull null

                    Message(
                        id = child.key ?: return@mapNotNull null,
                        senderId = senderId,
                        senderName = child.child("senderName").getValue(String::class.java) ?: "User",
                        content = child.child("content").getValue(String::class.java) ?: "",
                        timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L,
                        isMine = senderId == myUid,
                        isDeletedForEveryone = isDeleted,
                        deletedForUsers = delMap?.keys?.filterIsInstance<String>()?.associateWith { true } ?: emptyMap(),
                        fileUrl = child.child("fileUrl").getValue(String::class.java),
                        fileName = child.child("fileName").getValue(String::class.java),
                        fileType = child.child("fileType").getValue(String::class.java)
                    )
                }
                trySend(msgs)
            }
            override fun onCancelled(error: DatabaseError) {
                if (error.code == DatabaseError.PERMISSION_DENIED) close() 
                else close(error.toException())
            }
        }
        val ref = messagesRef.child(groupId)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun sendCustomGroupMessage(groupId: String, content: String, fileUrl: String?, fileName: String?, fileType: String?): Result<Unit> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val userSnap = db.getReference("users").child(myUid).get().await()
            val myName = userSnap.child("username").getValue(String::class.java) ?: "User"

            val messageId = messagesRef.child(groupId).push().key ?: UUID.randomUUID().toString()
            val msgData = mutableMapOf<String, Any>(
                "senderId" to myUid,
                "senderName" to myName,
                "content" to content,
                "timestamp" to System.currentTimeMillis()
            )
            fileUrl?.let { msgData["fileUrl"] = it }
            fileName?.let { msgData["fileName"] = it }
            fileType?.let { msgData["fileType"] = it }
            
            // Save message
            messagesRef.child(groupId).child(messageId).setValue(msgData).await()
            
            // Update group last message
            val groupUpdates = mapOf(
                "lastMessage" to "$myName: $content",
                "lastTimestamp" to System.currentTimeMillis()
            )
            groupsRef.child(groupId).updateChildren(groupUpdates).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessageForMe(groupId: String, messageId: String): Result<Unit> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            messagesRef.child(groupId).child(messageId).child("deletedForUsers").child(myUid).setValue(true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessageForEveryone(groupId: String, messageId: String): Result<Unit> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            
            // Fetch group info to check adminId
            val groupSnap = groupsRef.child(groupId).get().await()
            val group = groupSnap.getValue(com.example.securechat.domain.model.CustomGroup::class.java) 
                ?: throw Exception("Không tìm thấy nhóm")
            
            val msgRef = messagesRef.child(groupId).child(messageId)
            val msgSnap = msgRef.get().await()
            val senderId = msgSnap.child("senderId").getValue(String::class.java)
            
            // Check if sender OR admin
            if (myUid == senderId || myUid == group.adminId) {
                msgRef.child("deletedForEveryone").setValue(true).await()
                msgRef.child("content").setValue("Tin nhắn đã được thu hồi").await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Chỉ người gửi hoặc Quản trị viên mới có quyền thu hồi tin nhắn"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
