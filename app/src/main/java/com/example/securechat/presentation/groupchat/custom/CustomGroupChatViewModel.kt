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
import com.example.securechat.domain.repository.FileStorageRepository
import android.net.Uri

@HiltViewModel
class CustomGroupChatViewModel @Inject constructor(
    private val groupRepository: CustomGroupRepository,
    private val fileStorageRepository: FileStorageRepository,
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
    
    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            if (forEveryone) {
                groupRepository.deleteMessageForEveryone(groupId, messageId)
            } else {
                groupRepository.deleteMessageForMe(groupId, messageId)
            }
        }
    }

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    fun sendAttachment(uri: Uri, content: String, fileName: String, fileType: String) {
        viewModelScope.launch {
            _isUploading.value = true
            val uploadResult = fileStorageRepository.uploadFile(uri, "custom_group_attachments")
            _isUploading.value = false
            
            uploadResult.onSuccess { url ->
                groupRepository.sendCustomGroupMessage(
                    groupId = groupId,
                    content = content,
                    fileUrl = url,
                    fileName = fileName,
                    fileType = fileType
                )
            }.onFailure {
                // handle error
            }
        }
    }
}
