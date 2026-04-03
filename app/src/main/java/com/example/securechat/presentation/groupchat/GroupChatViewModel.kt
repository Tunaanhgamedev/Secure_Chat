package com.example.securechat.presentation.groupchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.domain.model.Message
import com.example.securechat.domain.usecase.GetGroupMessagesUseCase
import com.example.securechat.domain.usecase.SendGroupMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val getGroupMessagesUseCase: GetGroupMessagesUseCase,
    private val sendGroupMessageUseCase: SendGroupMessageUseCase,
    private val chatRepository: com.example.securechat.domain.repository.ChatRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    init {
        viewModelScope.launch {
            try { getGroupMessagesUseCase().collectLatest { _messages.value = it } }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            try { sendGroupMessageUseCase(content.trim()) }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            if (forEveryone) {
                chatRepository.deleteMessageForEveryone("group", messageId, isGroup = true)
            } else {
                chatRepository.deleteMessageForMe("group", messageId, isGroup = true)
            }
        }
    }
}
