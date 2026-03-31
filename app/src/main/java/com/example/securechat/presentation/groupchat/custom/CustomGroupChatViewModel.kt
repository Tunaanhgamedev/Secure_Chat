package com.example.securechat.presentation.groupchat.custom

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.domain.model.CustomGroup
import com.example.securechat.domain.model.Message
import com.example.securechat.domain.repository.CustomGroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomGroupChatViewModel @Inject constructor(
    private val groupRepository: CustomGroupRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _groupInfo = MutableStateFlow<CustomGroup?>(null)
    val groupInfo: StateFlow<CustomGroup?> = _groupInfo.asStateFlow()

    init {
        // Load messages
        viewModelScope.launch {
            groupRepository.getCustomGroupMessages(groupId).collectLatest { msgs ->
                _messages.value = msgs.sortedBy { it.timestamp }
            }
        }
        // Load group info
        viewModelScope.launch {
            groupRepository.getGroupInfo(groupId).collectLatest { info ->
                _groupInfo.value = info
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            groupRepository.sendCustomGroupMessage(groupId, content.trim())
        }
    }
}
