package com.example.securechat.domain.repository

import android.net.Uri

interface FileStorageRepository {
    suspend fun uploadFile(uri: Uri, folder: String): Result<String>
}
