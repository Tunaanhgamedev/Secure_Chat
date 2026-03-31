package com.example.securechat.presentation.groupchat.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.ChatRepository
import com.example.securechat.domain.repository.CustomGroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGroupState(
    val groupName: String = "",
    val isPrivate: Boolean = false,
    val selectedMembers: Set<String> = emptySet(),
    val friends: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successGroupId: String? = null
)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val groupRepository: CustomGroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupState())
    val uiState: StateFlow<CreateGroupState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.getFriends().collectLatest { users ->
                _uiState.update { it.copy(friends = users) }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(groupName = name) }
    }

    fun onTypeChange(isPrivate: Boolean) {
        _uiState.update { it.copy(isPrivate = isPrivate) }
    }

    fun toggleMember(userId: String) {
        _uiState.update { state ->
            val updated = state.selectedMembers.toMutableSet()
            if (updated.contains(userId)) updated.remove(userId) else updated.add(userId)
            state.copy(selectedMembers = updated)
        }
    }

    fun createGroup() {
        val state = _uiState.value
        if (state.groupName.isBlank()) {
            _uiState.update { it.copy(error = "Vui lòng nhập tên nhóm") }
            return
        }
        if (state.selectedMembers.isEmpty()) {
            _uiState.update { it.copy(error = "Vui lòng chọn ít nhất 1 thành viên") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val type = if (state.isPrivate) "private" else "public"
            val result = groupRepository.createGroup(state.groupName, type, state.selectedMembers.toList())
            
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successGroupId = result.getOrNull()) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
