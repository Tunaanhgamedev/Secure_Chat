package com.example.securechat.domain.usecase

import com.example.securechat.domain.repository.ChatRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(otherUserId: String, content: String) {
        if (content.isBlank()) return
        repository.sendMessage(otherUserId, content)
    }
}
