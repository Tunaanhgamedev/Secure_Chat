package com.example.securechat.data.repository

import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    private val cachedUser = MutableStateFlow<User?>(null)
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val currentUser = auth.currentUser
            if (currentUser != null) {
                cachedUser.value = User(
                    id = currentUser.uid,
                    username = currentUser.displayName ?: "",
                    email = currentUser.email ?: "",
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
                val user = User(
                    id = firebaseUser.uid,
                    username = firebaseUser.displayName ?: "",
                    email = firebaseUser.email ?: "",
                    token = null
                )
                cachedUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Unknown authentication error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(username: String, email: String, password: String): Result<User> {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                    displayName = username
                }
                firebaseUser.updateProfile(profileUpdates).await()

                val user = User(
                    id = firebaseUser.uid,
                    username = username,
                    email = email,
                    token = null
                )
                
                usersRef.child(user.id).setValue(mapOf(
                    "username" to username,
                    "email" to email
                )).await()

                cachedUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Unknown authentication error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getCachedUser(): Flow<User?> = cachedUser

    override suspend fun logout() {
        firebaseAuth.signOut()
        cachedUser.value = null
    }
}
