package com.example.securechat.data.repository

import android.net.Uri
import com.example.securechat.domain.repository.FileStorageRepository
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import java.util.UUID

class FileStorageRepositoryImpl @Inject constructor() : FileStorageRepository {

    private val storage = FirebaseStorage.getInstance()

    override suspend fun uploadFile(uri: Uri, folder: String): Result<String> {
        return try {
            val fileName = UUID.randomUUID().toString()
            val storageRef = storage.reference.child("$folder/$fileName")
            
            // Upload file
            storageRef.putFile(uri).await()
            
            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
