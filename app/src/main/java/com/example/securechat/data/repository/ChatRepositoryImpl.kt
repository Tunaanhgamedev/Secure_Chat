package com.example.securechat.data.repository

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
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor() : ChatRepository {

    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun getUsers(): Flow<List<User>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = mutableListOf<User>()
                val myUid = auth.currentUser?.uid
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    if (uid == myUid) continue // Bỏ qua bản thân
                    
                    val email = child.child("email").getValue(String::class.java) ?: ""
                    val username = child.child("username").getValue(String::class.java) ?: email
                    users.add(User(id = uid, username = username, email = email, token = null))
                }
                trySend(users)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        val ref = db.getReference("users")
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun getMessages(otherUserId: String): Flow<List<Message>> = callbackFlow {
        val myId = auth.currentUser?.uid ?: return@callbackFlow
        val chatId = getChatId(myId, otherUserId)
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val msgs = mutableListOf<Message>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    val senderId = child.child("senderId").getValue(String::class.java) ?: ""
                    val content = child.child("content").getValue(String::class.java) ?: ""
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val senderName = child.child("senderName").getValue(String::class.java) ?: ""
                    
                    msgs.add(
                        Message(
                            id = id,
                            senderId = senderId,
                            senderName = senderName,
                            content = content,
                            timestamp = timestamp,
                            isMine = senderId == myId
                        )
                    )
                }
                trySend(msgs)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        val ref = db.getReference("messages").child(chatId)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun sendMessage(otherUserId: String, content: String) {
        val myId = auth.currentUser?.uid ?: return
        val chatId = getChatId(myId, otherUserId)
        val myName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Me"
        
        val msgRef = db.getReference("messages").child(chatId).push()
        val messageMap = mapOf(
            "senderId" to myId,
            "senderName" to myName,
            "content" to content,
            "timestamp" to System.currentTimeMillis()
        )
        msgRef.setValue(messageMap).await()
    }

    private fun getChatId(id1: String, id2: String): String {
        return if (id1 < id2) "${id1}_$id2" else "${id2}_$id1"
    }
}
