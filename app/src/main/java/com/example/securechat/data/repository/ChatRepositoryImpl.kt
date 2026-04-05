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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : ChatRepository {

    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                        photoUrl = child.child("photoUrl").getValue(String::class.java),
                        isOnline = child.child("isOnline").getValue(Boolean::class.java) ?: false,
                        lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L,
                        isPresenceHidden = child.child("isPresenceHidden").getValue(Boolean::class.java) ?: false
                    )
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

    // ─── Conversations & Message Requests ────────────────────────────────────────
    override fun getConversations(): Flow<List<Conversation>> = getConversationFlow("conversations")
    override fun getMessageRequests(): Flow<List<Conversation>> = getConversationFlow("message_requests")

    private fun getConversationFlow(node: String): Flow<List<Conversation>> = callbackFlow {
        val myUid = auth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch {
                    val conversations = snapshot.children.mapNotNull { child ->
                        val peerId = child.child("peerId").getValue(String::class.java) ?: return@mapNotNull null
                        
                        // Fetch presence and photo for peer
                        val peerSnap = db.getReference("users").child(peerId).get().await()
                        val isOnline = peerSnap.child("isOnline").getValue(Boolean::class.java) ?: false
                        val lastSeen = peerSnap.child("lastSeen").getValue(Long::class.java) ?: 0L
                        val isHidden = peerSnap.child("isPresenceHidden").getValue(Boolean::class.java) ?: false
                        val photoUrl = peerSnap.child("photoUrl").getValue(String::class.java)

                        Conversation(
                            peerId       = peerId,
                            peerName     = child.child("peerName").getValue(String::class.java) ?: "",
                            peerEmail    = child.child("peerEmail").getValue(String::class.java) ?: "",
                            lastMessage  = child.child("lastMessage").getValue(String::class.java) ?: "",
                            lastTimestamp = child.child("lastTimestamp").getValue(Long::class.java) ?: 0L,
                            isOnline     = if (isHidden) false else isOnline,
                            lastSeen     = lastSeen,
                            peerPhotoUrl = photoUrl
                        )
                    }.sortedByDescending { it.lastTimestamp }
                    trySend(conversations)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        val ref = db.getReference(node).child(myUid)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─── Friend System ──────────────────────────────────────────────────────────
    override fun getFriends(): Flow<List<User>> = callbackFlow {
        val myUid = auth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch {
                    val friends = snapshot.children.mapNotNull { child ->
                        val friendId = child.key ?: return@mapNotNull null
                        val userSnap = db.getReference("users").child(friendId).get().await()
                        User(
                            id = friendId,
                            username = userSnap.child("username").getValue(String::class.java) ?: "",
                            email = userSnap.child("email").getValue(String::class.java) ?: "",
                            photoUrl = userSnap.child("photoUrl").getValue(String::class.java)
                        )
                    }
                    trySend(friends)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val ref = db.getReference("friends").child(myUid)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
    

    override fun getFriendRequests(): Flow<List<User>> = callbackFlow {
        val myUid = auth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch {
                    val users = snapshot.children.mapNotNull { child ->
                        val senderId = child.key ?: return@mapNotNull null
                        val userSnap = db.getReference("users").child(senderId).get().await()
                        User(
                            id = senderId,
                            username = userSnap.child("username").getValue(String::class.java) ?: "",
                            email = userSnap.child("email").getValue(String::class.java) ?: "",
                            photoUrl = userSnap.child("photoUrl").getValue(String::class.java)
                        )
                    }
                    trySend(users)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val ref = db.getReference("friend_requests").child(myUid)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun sendFriendRequest(targetUserId: String) {
        val myUid = auth.currentUser?.uid ?: return
        db.getReference("friend_requests").child(targetUserId).child(myUid).setValue(true).await()
    }

    override suspend fun acceptFriendRequest(senderUserId: String) {
        val myUid = auth.currentUser?.uid ?: return
        db.getReference("friends").child(myUid).child(senderUserId).setValue(true)
        db.getReference("friends").child(senderUserId).child(myUid).setValue(true)
        db.getReference("friend_requests").child(myUid).child(senderUserId).removeValue()
        
        // Move message request to conversations if exists
        val reqSnap = db.getReference("message_requests").child(myUid).child(senderUserId).get().await()
        if (reqSnap.exists()) {
            val data = reqSnap.value
            db.getReference("conversations").child(myUid).child(senderUserId).setValue(data)
            db.getReference("message_requests").child(myUid).child(senderUserId).removeValue()
            
            // Also move for the other side
            val otherReqSnap = db.getReference("message_requests").child(senderUserId).child(myUid).get().await()
            if (otherReqSnap.exists()) {
                db.getReference("conversations").child(senderUserId).child(myUid).setValue(otherReqSnap.value)
                db.getReference("message_requests").child(senderUserId).child(myUid).removeValue()
            }
        }
    }

    override suspend fun rejectFriendRequest(senderUserId: String) {
        val myUid = auth.currentUser?.uid ?: return
        db.getReference("friend_requests").child(myUid).child(senderUserId).removeValue()
    }

    override fun isFriend(userId: String): Flow<Boolean> = callbackFlow {
        val myUid = auth.currentUser?.uid ?: run { trySend(false); close(); return@callbackFlow }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot.exists()) }
            override fun onCancelled(error: DatabaseError) {}
        }
        val ref = db.getReference("friends").child(myUid).child(userId)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─── Messaging ─────────────────────────────────────────────────────────────
    override fun getMessages(otherUserId: String): Flow<List<Message>> {
        val myId   = auth.currentUser?.uid ?: return flowOf(emptyList())
        val chatId = chatId(myId, otherUserId)
        return callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = snapshot.children.mapNotNull { child ->
                        val delMap = child.child("deletedForUsers").value as? Map<*, *>
                        val delUids = delMap?.keys?.filterIsInstance<String>()?.joinToString(",") ?: ""
                        
                        MessageEntity(
                            id                   = child.key ?: return@mapNotNull null,
                            senderId             = child.child("senderId").getValue(String::class.java) ?: "",
                            senderName           = child.child("senderName").getValue(String::class.java) ?: "",
                            content              = child.child("content").getValue(String::class.java) ?: "",
                            timestamp            = child.child("timestamp").getValue(Long::class.java) ?: 0L,
                            isMine               = child.child("senderId").getValue(String::class.java) == myId,
                            chatId               = chatId,
                            isDeletedForEveryone = child.child("deletedForEveryone").getValue(Boolean::class.java) ?: false,
                            deletedByUsers       = delUids,
                            fileUrl              = child.child("fileUrl").getValue(String::class.java),
                            fileName             = child.child("fileName").getValue(String::class.java),
                            fileType             = child.child("fileType").getValue(String::class.java)
                        )
                    }
                    launch { messageDao.insertMessages(messages) }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            val ref = db.getReference("messages").child(chatId)
            ref.addValueEventListener(listener)
            val localFlow = messageDao.getMessages(chatId).map { entities -> 
                entities.map { it.toDomain() }.filter { !it.deletedForUsers.containsKey(myId) } 
            }
            launch { localFlow.collect { trySend(it) } }
            awaitClose { ref.removeEventListener(listener) }
        }
    }

    override suspend fun sendMessage(otherUserId: String, content: String, fileUrl: String?, fileName: String?, fileType: String?) {
        val myId   = auth.currentUser?.uid ?: return
        val myName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Tôi"
        val chatId = chatId(myId, otherUserId)
        val msgId     = db.getReference("messages").child(chatId).push().key ?: return
        val timestamp = System.currentTimeMillis()
        
        val msgData = mutableMapOf<String, Any>(
            "senderId"   to myId,
            "senderName" to myName,
            "content"    to content,
            "timestamp"  to timestamp
        )
        fileUrl?.let { msgData["fileUrl"] = it }
        fileName?.let { msgData["fileName"] = it }
        fileType?.let { msgData["fileType"] = it }

        db.getReference("messages").child(chatId).child(msgId).setValue(msgData).await()

        val friendStatus = isFriend(otherUserId).first()
        val node = if (friendStatus) "conversations" else "message_requests"
        updateConversationsSync(myId, otherUserId, content, timestamp, node)
    }

    // ─── Group Chat ──────────────────────────────────────────────────────────────
    override fun getGroupMessages(): Flow<List<Message>> {
        val myId = auth.currentUser?.uid
        val chatId = "group"
        return callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = snapshot.children.mapNotNull { child ->
                        val delMap = child.child("deletedForUsers").value as? Map<*, *>
                        val delUids = delMap?.keys?.filterIsInstance<String>()?.joinToString(",") ?: ""

                        MessageEntity(
                            id                   = child.key ?: return@mapNotNull null,
                            senderId             = child.child("senderId").getValue(String::class.java) ?: "",
                            senderName           = child.child("senderName").getValue(String::class.java) ?: "",
                            content              = child.child("content").getValue(String::class.java) ?: "",
                            timestamp            = child.child("timestamp").getValue(Long::class.java) ?: 0L,
                            isMine               = child.child("senderId").getValue(String::class.java) == myId,
                            chatId               = chatId,
                            isDeletedForEveryone = child.child("deletedForEveryone").getValue(Boolean::class.java) ?: false,
                            deletedByUsers       = delUids,
                            fileUrl              = child.child("fileUrl").getValue(String::class.java),
                            fileName             = child.child("fileName").getValue(String::class.java),
                            fileType             = child.child("fileType").getValue(String::class.java)
                        )
                    }
                    launch { messageDao.insertMessages(messages) }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            val ref = db.getReference("group_messages")
            ref.addValueEventListener(listener)
            val localFlow = messageDao.getMessages(chatId).map { entities -> 
                entities.map { it.toDomain() }.filter { !it.deletedForUsers.containsKey(myId) }
            }
            launch { localFlow.collect { trySend(it) } }
            awaitClose { ref.removeEventListener(listener) }
        }
    }

    override suspend fun sendGroupMessage(content: String, fileUrl: String?, fileName: String?, fileType: String?) {
        val myId   = auth.currentUser?.uid ?: return
        val myName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Ẩn danh"
        
        val msgData = mutableMapOf<String, Any>(
            "senderId"   to myId,
            "senderName" to myName,
            "content"    to content,
            "timestamp"  to System.currentTimeMillis()
        )
        fileUrl?.let { msgData["fileUrl"] = it }
        fileName?.let { msgData["fileName"] = it }
        fileType?.let { msgData["fileType"] = it }

        db.getReference("group_messages").push().setValue(msgData).await()
    }

    private suspend fun updateConversationsSync(myId: String, otherUserId: String, content: String, ts: Long, node: String) {
        val peerSnap = db.getReference("users").child(otherUserId).get().await()
        val peerName  = peerSnap.child("username").getValue(String::class.java) ?: ""
        val peerEmail = peerSnap.child("email").getValue(String::class.java) ?: ""
        val mySnap    = db.getReference("users").child(myId).get().await()
        val myNameDb  = mySnap.child("username").getValue(String::class.java) ?: ""
        val myEmail   = mySnap.child("email").getValue(String::class.java) ?: ""

        val myData = mapOf("peerId" to otherUserId, "peerName" to peerName, "peerEmail" to peerEmail, "lastMessage" to content, "lastTimestamp" to ts)
        val peerData = mapOf("peerId" to myId, "peerName" to myNameDb, "peerEmail" to myEmail, "lastMessage" to content, "lastTimestamp" to ts)

        db.getReference(node).child(myId).child(otherUserId).setValue(myData)
        db.getReference(node).child(otherUserId).child(myId).setValue(peerData)
    }

    private fun MessageEntity.toDomain() = Message(
        id                   = id, 
        senderId             = senderId, 
        senderName           = senderName, 
        content              = content, 
        timestamp            = timestamp, 
        isMine               = isMine,
        isDeletedForEveryone = isDeletedForEveryone,
        deletedForUsers      = deletedByUsers.split(",").filter { it.isNotBlank() }.associateWith { true },
        fileUrl              = fileUrl,
        fileName             = fileName,
        fileType             = fileType
    )
    private fun chatId(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    // ─── Call Signaling ─────────────────────────────────────────────────────────

    override fun listenForIncomingCall(): Flow<com.example.securechat.domain.model.IncomingCallModel?> = callbackFlow {
        val myUid = auth.currentUser?.uid ?: run { trySend(null); close(); return@callbackFlow }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val callModel = snapshot.getValue(com.example.securechat.domain.model.IncomingCallModel::class.java)
                    trySend(callModel)
                } else {
                    trySend(null)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        val ref = db.getReference("incoming_calls").child(myUid)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun startCall(targetUserId: String, callerName: String, callerPhotoUrl: String?): Result<Unit> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val callModel = com.example.securechat.domain.model.IncomingCallModel(
                callerId = myUid,
                callerName = callerName,
                callerPhotoUrl = callerPhotoUrl,
                status = "ringing",
                timestamp = System.currentTimeMillis()
            )
            db.getReference("incoming_calls").child(targetUserId).setValue(callModel).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun respondToCall(callerId: String, status: String): Result<Unit> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            // Update the caller's node that I declined/accepted
            if (status == "ended" || status == "declined") {
                db.getReference("incoming_calls").child(myUid).removeValue().await()
                db.getReference("incoming_calls").child(callerId).child("status").setValue(status).await()
            } else {
                db.getReference("incoming_calls").child(myUid).child("status").setValue(status).await()
                db.getReference("incoming_calls").child(callerId).child("status").setValue(status).await() // Tell caller to proceed
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun listenForCallStatus(targetUserId: String): Flow<String?> = callbackFlow {
        val myUid = auth.currentUser?.uid ?: run { trySend(null); close(); return@callbackFlow }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val status = snapshot.child("status").getValue(String::class.java)
                    trySend(status)
                } else {
                    trySend(null)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        // I am the caller, I listen to my own incoming_calls node because the receiver replies back to me there
        // Or wait. When I start a call, I write to targetUserId
        // Receiver responds by setting my incoming_calls/myUid/status = accepted
        val ref = db.getReference("incoming_calls").child(myUid)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun endCallSignal(targetUserId: String): Result<Unit> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            db.getReference("incoming_calls").child(targetUserId).removeValue().await()
            db.getReference("incoming_calls").child(myUid).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun deleteMessageForMe(chatId: String, messageId: String, isGroup: Boolean): Result<Unit> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val baseNode = if (isGroup) "group_messages" else "messages/$chatId"
            db.getReference(baseNode).child(messageId).child("deletedForUsers").child(myUid).setValue(true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessageForEveryone(chatId: String, messageId: String, isGroup: Boolean): Result<Unit> {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val baseNode = if (isGroup) "group_messages" else "messages/$chatId"
            val ref = db.getReference(baseNode).child(messageId)
            
            val snap = ref.get().await()
            val senderId = snap.child("senderId").getValue(String::class.java)
            
            // For global/private chat, only sender can delete for everyone.
            // Custom groups handle admin rights in their own repository.
            if (senderId == myUid) {
                ref.child("deletedForEveryone").setValue(true).await()
                ref.child("content").setValue("Tin nhắn đã được thu hồi").await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Bạn không thể thu hồi tin nhắn của người khác"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
