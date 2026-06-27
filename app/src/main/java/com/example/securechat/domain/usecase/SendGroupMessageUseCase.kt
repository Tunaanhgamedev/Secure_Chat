package com.example.securechat.domain.usecase

import com.example.securechat.domain.repository.ChatRepository
import javax.inject.Inject

class SendGroupMessageUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(content: String, fileUrl: String? = null, fileName: String? = null, fileType: String? = null) {
        if (content.isBlank() && fileUrl == null) return
        repository.sendGroupMessage(content, fileUrl, fileName, fileType)
    }
}
