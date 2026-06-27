package com.example.securechat.domain.usecase

import com.example.securechat.domain.repository.ChatRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(otherUserId: String, content: String, fileUrl: String? = null, fileName: String? = null, fileType: String? = null) {
        if (content.isBlank() && fileUrl == null) return
        repository.sendMessage(otherUserId, content, fileUrl, fileName, fileType)
    }
}
