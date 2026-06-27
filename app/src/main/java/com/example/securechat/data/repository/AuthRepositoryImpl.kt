package com.example.securechat.data.repository

import android.net.Uri
import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.AuthRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    private val cachedUser = MutableStateFlow<User?>(null)
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")
    private val storage = FirebaseStorage.getInstance().getReference("avatars")
    
    // Manage listener to avoid memory leak and freeze
    private var currentUserListener: ValueEventListener? = null
    private var currentPeerId: String? = null

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val currentUser = auth.currentUser
            
            // Remove previous listener if exists
            currentPeerId?.let { uid ->
                currentUserListener?.let { usersRef.child(uid).removeEventListener(it) }
            }

            if (currentUser != null) {
                currentPeerId = currentUser.uid
                currentUserListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        cachedUser.value = User(
                            id = currentUser.uid,
                            username = snapshot.child("username").getValue(String::class.java) ?: currentUser.displayName ?: "",
                            email = snapshot.child("email").getValue(String::class.java) ?: currentUser.email ?: "",
                            photoUrl = snapshot.child("photoUrl").getValue(String::class.java),
                            isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false,
                            lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L,
                            isPresenceHidden = snapshot.child("isPresenceHidden").getValue(Boolean::class.java) ?: false
                        )
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                usersRef.child(currentUser.uid).addValueEventListener(currentUserListener!!)
            } else {
                cachedUser.value = null
                currentPeerId = null
                currentUserListener = null
            }
        }
    }

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                val userSnap = usersRef.child(firebaseUser.uid).get().await()
                val isHidden = userSnap.child("isPresenceHidden").getValue(Boolean::class.java) ?: false
                
                syncToUsersNode(firebaseUser.uid, firebaseUser.displayName ?: "", firebaseUser.email ?: "", firebaseUser.photoUrl?.toString(), isHidden)
                setupPresence(firebaseUser.uid, isHidden)

                val user = User(
                    id = firebaseUser.uid,
                    username = firebaseUser.displayName ?: "",
                    email = firebaseUser.email ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    isPresenceHidden = isHidden
                )
                cachedUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Lỗi xác thực không xác định"))
            }
        } catch (e: Exception) {
            Result.failure(translateAuthError(e))
        }
    }

    override suspend fun register(username: String, email: String, password: String): Result<User> {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                val profileUpdates = userProfileChangeRequest {
                    displayName = username
                }
                firebaseUser.updateProfile(profileUpdates).await()

                syncToUsersNode(firebaseUser.uid, username, email, null, false)
                setupPresence(firebaseUser.uid, false)

                val user = User(id = firebaseUser.uid, username = username, email = email)
                cachedUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Lỗi đăng ký không xác định"))
            }
        } catch (e: Exception) {
            Result.failure(translateAuthError(e))
        }
    }

    private suspend fun syncToUsersNode(uid: String, username: String, email: String, photoUrl: String?, isHidden: Boolean) {
        usersRef.child(uid).updateChildren(mapOf(
            "username" to username,
            "email" to email,
            "photoUrl" to photoUrl,
            "isPresenceHidden" to isHidden
        )).await()
    }

    private fun setupPresence(uid: String, isHidden: Boolean) {
        if (isHidden) return
        val userStatusRef = usersRef.child(uid)
        
        userStatusRef.child("isOnline").onDisconnect().setValue(false)
        userStatusRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
        userStatusRef.child("isOnline").setValue(true)
    }

    override suspend fun updatePresence(isOnline: Boolean, isHidden: Boolean?): Result<Unit> = try {
        val uid = firebaseAuth.currentUser?.uid ?: run { return Result.success(Unit) } // Fix: Gracefully exit instead of crash on logout
        val updates = mutableMapOf<String, Any>("isOnline" to isOnline)
        if (isOnline) updates["lastSeen"] = System.currentTimeMillis()
        isHidden?.let { 
            updates["isPresenceHidden"] = it
            if (it) {
                updates["isOnline"] = false
                usersRef.child(uid).child("isOnline").onDisconnect().cancel()
                usersRef.child(uid).child("lastSeen").onDisconnect().cancel()
            } else {
                setupPresence(uid, false)
            }
        }
        usersRef.child(uid).updateChildren(updates).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun getCachedUser(): Flow<User?> = cachedUser

    override suspend fun logout() {
        val uid = firebaseAuth.currentUser?.uid
        
        // 1. Remove listener immediately from the database to stop updates
        currentPeerId?.let { peerId ->
            currentUserListener?.let { usersRef.child(peerId).removeEventListener(it) }
        }
        currentPeerId = null
        currentUserListener = null

        // 2. Clear cached user state so UI reflects logout immediately
        cachedUser.value = null

        // 3. Try to mark user as offline on Firebase, but don't let failures block signOut
        if (uid != null) {
            try {
                usersRef.child(uid).child("isOnline").setValue(false)
                usersRef.child(uid).child("lastSeen").setValue(ServerValue.TIMESTAMP).await()
            } catch (e: Exception) {
                // If network fails, we still want to proceed to signOut
            }
        }

        // 4. Definitive sign out from Firebase Auth
        firebaseAuth.signOut()
    }

    // ─── Pro Features ─────────────────────────────────────────────────────────────
    override suspend fun reauthenticate(password: String): Result<Unit> = try {
        val user = firebaseAuth.currentUser ?: throw Exception("Chưa đăng nhập")
        val credential = EmailAuthProvider.getCredential(user.email!!, password)
        user.reauthenticate(credential).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(translateAuthError(e))
    }

    override suspend fun updateProfile(username: String, photoUrl: String?): Result<Unit> = try {
        val user = firebaseAuth.currentUser ?: throw Exception("Chưa đăng nhập")
        val profileUpdates = userProfileChangeRequest {
            displayName = username
            photoUrl?.let { photoUri = Uri.parse(it) }
        }
        user.updateProfile(profileUpdates).await()
        val isHidden = cachedUser.value?.isPresenceHidden ?: false
        syncToUsersNode(user.uid, username, user.email!!, photoUrl, isHidden)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateEmail(newEmail: String): Result<Unit> = try {
        val user = firebaseAuth.currentUser ?: throw Exception("Chưa đăng nhập")
        user.updateEmail(newEmail).await()
        val isHidden = cachedUser.value?.isPresenceHidden ?: false
        syncToUsersNode(user.uid, user.displayName ?: "", newEmail, user.photoUrl?.toString(), isHidden)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(translateAuthError(e))
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> = try {
        val user = firebaseAuth.currentUser ?: throw Exception("Chưa đăng nhập")
        user.updatePassword(newPassword).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(translateAuthError(e))
    }

    override suspend fun uploadAvatar(uri: Uri): Result<String> = try {
        val user = firebaseAuth.currentUser ?: throw Exception("Chưa đăng nhập")
        val fileRef = storage.child("${user.uid}.jpg")
        fileRef.putFile(uri).await()
        val downloadUrl = fileRef.downloadUrl.await().toString()
        Result.success(downloadUrl)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun translateAuthError(e: Exception): Exception {
        val message = when (e) {
            is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "Tài khoản không tồn tại hoặc đã bị vô hiệu hóa."
            is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Email hoặc mật khẩu không chính xác."
            is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "Email này đã được sử dụng bởi một tài khoản khác."
            is com.google.firebase.FirebaseNetworkException -> "Lỗi kết nối mạng, vui lòng kiểm tra lại."
            else -> e.message ?: "Đã xảy ra lỗi hệ thống."
        }
        return Exception(message)
    }
}
