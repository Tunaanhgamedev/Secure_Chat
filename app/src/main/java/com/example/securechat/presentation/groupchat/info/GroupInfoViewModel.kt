package com.example.securechat.presentation.groupchat.info

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.domain.model.CustomGroup
import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.AuthRepository
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

data class GroupInfoState(
    val group: CustomGroup? = null,
    val membersInfo: List<User> = emptyList(),
    val pendingInfo: List<User> = emptyList(),
    val allFriends: List<User> = emptyList(),
    val myUid: String = "",
    val isAdmin: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    private val groupRepository: CustomGroupRepository,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(GroupInfoState())
    val uiState: StateFlow<GroupInfoState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getCachedUser().collectLatest { user ->
                _uiState.update { it.copy(myUid = user?.id ?: "") }
            }
        }

        viewModelScope.launch {
            chatRepository.getFriends().collectLatest { friends ->
                _uiState.update { it.copy(allFriends = friends) }
            }
        }

        viewModelScope.launch {
            groupRepository.getGroupInfo(groupId).collectLatest { group ->
                _uiState.update { it.copy(group = group, isAdmin = group?.adminId == _uiState.value.myUid) }
                group?.let { loadUserInfo(it) }
            }
        }
    }

    private fun loadUserInfo(group: CustomGroup) {
        viewModelScope.launch {
            chatRepository.getUsers().collectLatest { allUsers ->
                val members = allUsers.filter { group.members.containsKey(it.id) }
                val pendings = allUsers.filter { group.pendingRequests.containsKey(it.id) }
                _uiState.update {
                    it.copy(
                        membersInfo = members,
                        pendingInfo = pendings,
                        isAdmin = group.adminId == it.myUid
                    )
                }
            }
        }
    }

    fun addMember(userId: String) {
        viewModelScope.launch {
            val result = groupRepository.addMemberRequest(groupId, userId)
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun approveMember(userId: String) {
        viewModelScope.launch {
            val result = groupRepository.approveMember(groupId, userId)
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun removeMemberOrRequest(userId: String) {
        viewModelScope.launch {
            val result = groupRepository.removeMemberOrRequest(groupId, userId)
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun leaveGroup(onLeft: () -> Unit) {
        viewModelScope.launch {
            val result = groupRepository.leaveGroup(groupId)
            if (result.isSuccess) {
                onLeft()
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
