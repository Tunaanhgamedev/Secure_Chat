package com.example.securechat.domain.usecase

import com.example.securechat.domain.repository.ChatRepository
import javax.inject.Inject

class SendGroupMessageUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(content: String) {
        if (content.isBlank()) return
        repository.sendGroupMessage(content)
    }
}
