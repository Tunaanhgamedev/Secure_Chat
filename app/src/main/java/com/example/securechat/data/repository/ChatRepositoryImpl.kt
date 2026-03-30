package com.example.securechat.data.repository

import com.example.securechat.data.local.dao.MessageDao
import com.example.securechat.data.local.entity.MessageEntity
import com.example.securechat.domain.model.Conversation
import com.example.securechat.domain.model.Message
import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : ChatRepository {

    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ─── User Discovery ─────────────────────────────────────────────────────────
    override fun getUsers(): Flow<List<User>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val myUid = auth.currentUser?.uid
                val users = snapshot.children.mapNotNull { child ->
                    val uid = child.key ?: return@mapNotNull null
                    if (uid == myUid) return@mapNotNull null
                    User(
                        id = uid,
                        username = child.child("username").getValue(String::class.java) ?: "",
                        email = child.child("email").getValue(String::class.java) ?: "",
                    )
                }
                trySend(users)
            }
            override fun onCancelled(error: DatabaseError) = close(error.toException())
        }
        val ref = db.getReference("users")
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─── Conversations (recent chats) ────────────────────────────────────────────
    override fun getConversations(): Flow<List<Conversation>> = callbackFlow {
        val myUid = auth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val conversations = snapshot.children.mapNotNull { child ->
                    Conversation(
                        peerId       = child.child("peerId").getValue(String::class.java) ?: return@mapNotNull null,
                        peerName     = child.child("peerName").getValue(String::class.java) ?: "",
                        peerEmail    = child.child("peerEmail").getValue(String::class.java) ?: "",
                        lastMessage  = child.child("lastMessage").getValue(String::class.java) ?: "",
                        lastTimestamp = child.child("lastTimestamp").getValue(Long::class.java) ?: 0L
                    )
                }.sortedByDescending { it.lastTimestamp }
                trySend(conversations)
            }
            override fun onCancelled(error: DatabaseError) = close(error.toException())
        }
        val ref = db.getReference("conversations").child(myUid)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─── 1-1 Messages with Offline Caching ───────────────────────────────────────
    override fun getMessages(otherUserId: String): Flow<List<Message>> {
        val myId = auth.currentUser?.uid ?: return callbackFlow { trySend(emptyList()); close() }
        val chatId = chatId(myId, otherUserId)

        // Remote listener to update local DB
        syncFirebaseToLocal(db.getReference("messages").child(chatId), chatId)

        // Return flow from local DB
        return messageDao.getMessages(chatId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun sendMessage(otherUserId: String, content: String) {
        val myId   = auth.currentUser?.uid ?: return
        val myName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Tôi"
        val chatId = chatId(myId, otherUserId)

        // Save locally first for optimistic UI (optional, but good)
        val msgId = db.getReference("messages").child(chatId).push().key ?: return
        val timestamp = System.currentTimeMillis()
        
        // Save to Remote (Firebase)
        db.getReference("messages").child(chatId).child(msgId).setValue(mapOf(
            "senderId"   to myId,
            "senderName" to myName,
            "content"    to content,
            "timestamp"  to timestamp
        )).await()

        // Update conversations
        updateConversationsSync(myId, otherUserId, content, timestamp)
    }

    // ─── Group Chat with Offline Caching ─────────────────────────────────────────
    override fun getGroupMessages(): Flow<List<Message>> {
        val chatId = "group"
        
        // Remote listener to update local DB
        syncFirebaseToLocal(db.getReference("group_messages"), chatId)

        // Return flow from local DB
        return messageDao.getMessages(chatId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun sendGroupMessage(content: String) {
        val myId   = auth.currentUser?.uid ?: return
        val myName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Ẩn danh"
        db.getReference("group_messages").push().setValue(mapOf(
            "senderId"   to myId,
            "senderName" to myName,
            "content"    to content,
            "timestamp"  to System.currentTimeMillis()
        )).await()
    }

    // ─── Sync Helper ──────────────────────────────────────────────────────────────
    private fun syncFirebaseToLocal(ref: com.google.firebase.database.DatabaseReference, chatId: String) {
        val myId = auth.currentUser?.uid
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Background update using a temporary coroutine scope or GlobalScope (with care) 
                // In a real app, use WorkManager or a lifecycle-aware scope.
                // For simplicity here, we'll map and save.
                val entities = snapshot.children.mapNotNull { child ->
                    MessageEntity(
                        id         = child.key ?: return@mapNotNull null,
                        senderId   = child.child("senderId").getValue(String::class.java) ?: "",
                        senderName = child.child("senderName").getValue(String::class.java) ?: "",
                        content    = child.child("content").getValue(String::class.java) ?: "",
                        timestamp  = child.child("timestamp").getValue(Long::class.java) ?: 0L,
                        isMine     = child.child("senderId").getValue(String::class.java) == myId,
                        chatId     = chatId
                    )
                }
                // We'll use a hack to run this suspend function inside the callback
                kotlinx.coroutines.GlobalScope.launch {
                    messageDao.insertMessages(entities)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private suspend fun updateConversationsSync(myId: String, otherUserId: String, content: String, ts: Long) {
        // Fetch peer info then update both sides of /conversations
        val peerSnap = db.getReference("users").child(otherUserId).get().await()
        val peerName  = peerSnap.child("username").getValue(String::class.java) ?: ""
        val peerEmail = peerSnap.child("email").getValue(String::class.java) ?: ""
        val mySnap    = db.getReference("users").child(myId).get().await()
        val myNameDb  = mySnap.child("username").getValue(String::class.java) ?: ""
        val myEmail   = mySnap.child("email").getValue(String::class.java) ?: ""

        db.getReference("conversations").child(myId).child(otherUserId).setValue(mapOf(
            "peerId"        to otherUserId,
            "peerName"      to peerName,
            "peerEmail"     to peerEmail,
            "lastMessage"   to content,
            "lastTimestamp" to ts
        ))
        db.getReference("conversations").child(otherUserId).child(myId).setValue(mapOf(
            "peerId"        to myId,
            "peerName"      to myNameDb,
            "peerEmail"     to myEmail,
            "lastMessage"   to content,
            "lastTimestamp" to ts
        ))
    }

    // ─── Mappings ──────────────────────────────────────────────────────────────────
    private fun MessageEntity.toDomain() = Message(
        id         = id,
        senderId   = senderId,
        senderName = senderName,
        content    = content,
        timestamp  = timestamp,
        isMine     = isMine
    )

    // ─── Helpers ─────────────────────────────────────────────────────────────────
    private fun chatId(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"
}
