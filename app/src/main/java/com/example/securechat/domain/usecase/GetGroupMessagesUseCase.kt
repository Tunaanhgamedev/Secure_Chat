package com.example.securechat.domain.usecase

import com.example.securechat.domain.model.Message
import com.example.securechat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetGroupMessagesUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    operator fun invoke(): Flow<List<Message>> = repository.getGroupMessages()
}
