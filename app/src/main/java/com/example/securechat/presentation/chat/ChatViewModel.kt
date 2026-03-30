package com.example.securechat.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.domain.model.Message
import com.example.securechat.domain.usecase.GetMessagesUseCase
import com.example.securechat.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val otherUserId: String? = savedStateHandle.get<String>("userId")
    val peerName: String = savedStateHandle.get<String>("peerName") ?: ""

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    init {
        otherUserId?.let { id ->
            viewModelScope.launch {
                try { getMessagesUseCase(id).collectLatest { _messages.value = it } }
                catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || otherUserId == null) return
        viewModelScope.launch {
            try { sendMessageUseCase(otherUserId, content.trim()) }
            catch (e: Exception) { e.printStackTrace() }
        }
    }
}
