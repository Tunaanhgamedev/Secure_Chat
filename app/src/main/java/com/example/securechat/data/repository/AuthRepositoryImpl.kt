package com.example.securechat.data.repository

import android.net.Uri
import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.AuthRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
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

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val currentUser = auth.currentUser
            if (currentUser != null) {
                cachedUser.value = User(
                    id = currentUser.uid,
                    username = currentUser.displayName ?: "",
                    email = currentUser.email ?: "",
                    photoUrl = currentUser.photoUrl?.toString(),
                    token = null
                )
            } else {
                cachedUser.value = null
            }
        }
    }

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                // Ensure record exists on login (syncing logic)
                syncToUsersNode(firebaseUser.uid, firebaseUser.displayName ?: "", firebaseUser.email ?: "", firebaseUser.photoUrl?.toString())

                val user = User(
                    id = firebaseUser.uid,
                    username = firebaseUser.displayName ?: "",
                    email = firebaseUser.email ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    token = null
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

                syncToUsersNode(firebaseUser.uid, username, email, null)

                val user = User(
                    id = firebaseUser.uid,
                    username = username,
                    email = email,
                    photoUrl = null,
                    token = null
                )
                cachedUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Lỗi đăng ký không xác định"))
            }
        } catch (e: Exception) {
            Result.failure(translateAuthError(e))
        }
    }

    private suspend fun syncToUsersNode(uid: String, username: String, email: String, photoUrl: String?) {
        usersRef.child(uid).updateChildren(mapOf(
            "username" to username,
            "email" to email,
            "photoUrl" to photoUrl
        )).await()
    }

    override fun getCachedUser(): Flow<User?> = cachedUser

    override suspend fun logout() {
        firebaseAuth.signOut()
        cachedUser.value = null
    }

    // ─── Pro Features (Re-auth, Updates) ───────────────────────────────────────────
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
        syncToUsersNode(user.uid, username, user.email!!, photoUrl)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateEmail(newEmail: String): Result<Unit> = try {
        val user = firebaseAuth.currentUser ?: throw Exception("Chưa đăng nhập")
        user.updateEmail(newEmail).await()
        syncToUsersNode(user.uid, user.displayName ?: "", newEmail, user.photoUrl?.toString())
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
        val uploadTask = fileRef.putFile(uri).await()
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
