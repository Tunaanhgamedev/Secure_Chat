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

import com.example.securechat.domain.repository.FileStorageRepository
import android.net.Uri

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val chatRepository: ChatRepository,
    private val fileStorageRepository: FileStorageRepository,
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
    
    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        otherUserId?.let { id ->
            viewModelScope.launch {
                if (forEveryone) {
                    chatRepository.deleteMessageForEveryone(id, messageId, isGroup = false)
                } else {
                    chatRepository.deleteMessageForMe(id, messageId, isGroup = false)
                }
            }
        }
    }

    fun sendFriendRequest() {
        otherUserId?.let { id ->
            viewModelScope.launch {
                chatRepository.sendFriendRequest(id)
            }
        }
    }

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    fun sendAttachment(uri: Uri, content: String, fileName: String, fileType: String) {
        if (otherUserId == null) return
        viewModelScope.launch {
            _isUploading.value = true
            val uploadResult = fileStorageRepository.uploadFile(uri, "attachments")
            _isUploading.value = false
            
            uploadResult.onSuccess { url ->
                sendMessageUseCase(
                    otherUserId = otherUserId,
                    content = content,
                    fileUrl = url,
                    fileName = fileName,
                    fileType = fileType
                )
            }.onFailure {
                // handle error if needed
            }
        }
    }
}
