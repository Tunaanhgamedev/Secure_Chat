package com.example.securechat.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.domain.model.Message
import com.example.securechat.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val otherUserId: String? = savedStateHandle.get<String>("userId")
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    init {
        otherUserId?.let { id ->
            viewModelScope.launch {
                try {
                    chatRepository.getMessages(id).collectLatest { msgs ->
                        _messages.value = msgs
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || otherUserId == null) return
        viewModelScope.launch {
            chatRepository.sendMessage(otherUserId, content.trim())
        }
    }
}
