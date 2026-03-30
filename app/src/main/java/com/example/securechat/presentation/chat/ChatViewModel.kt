package com.example.securechat.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.domain.model.Message
import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.ChatRepository
import com.example.securechat.domain.usecase.GetMessagesUseCase
import com.example.securechat.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val otherUserId: String? = savedStateHandle.get<String>("userId")
    val peerName: String = savedStateHandle.get<String>("peerName") ?: ""

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isFriend = MutableStateFlow(true)
    val isFriend: StateFlow<Boolean> = _isFriend

    private val _peerUser = MutableStateFlow<User?>(null)
    val peerUser: StateFlow<User?> = _peerUser

    init {
        otherUserId?.let { id ->
            viewModelScope.launch {
                getMessagesUseCase(id).collectLatest { _messages.value = it }
            }
            viewModelScope.launch {
                chatRepository.isFriend(id).collectLatest { _isFriend.value = it }
            }
            viewModelScope.launch {
                chatRepository.getUsers().collectLatest { users ->
                    _peerUser.value = users.find { it.id == id }
                }
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || otherUserId == null) return
        viewModelScope.launch {
            sendMessageUseCase(otherUserId, content.trim())
        }
    }
    
    fun sendFriendRequest() {
        otherUserId?.let { id ->
            viewModelScope.launch { chatRepository.sendFriendRequest(id) }
        }
    }
}
