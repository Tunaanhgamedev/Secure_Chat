package com.example.securechat.presentation.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.domain.model.IncomingCallModel
import com.example.securechat.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallManagerViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    val incomingCall: StateFlow<IncomingCallModel?> = chatRepository.listenForIncomingCall()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun acceptCall(callerId: String, onNavigate: () -> Unit) {
        viewModelScope.launch {
            chatRepository.respondToCall(callerId, "accepted")
            onNavigate()
        }
    }

    fun declineCall(callerId: String) {
        viewModelScope.launch {
            chatRepository.respondToCall(callerId, "declined")
        }
    }
}
