package com.example.securechat.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.domain.model.Conversation
import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.AuthRepository
import com.example.securechat.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeTab { MESSAGES, FIND_FRIENDS, REQUESTS, MESSAGE_REQUESTS }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val groupRepository: com.example.securechat.domain.repository.CustomGroupRepository
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _messageRequests = MutableStateFlow<List<Conversation>>(emptyList())
    val messageRequests: StateFlow<List<Conversation>> = _messageRequests

    private val _friendRequests = MutableStateFlow<List<User>>(emptyList())
    val friendRequests: StateFlow<List<User>> = _friendRequests

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val currentUser: StateFlow<User?> = authRepository.getCachedUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val filteredUsers: StateFlow<List<User>> = combine(_allUsers, _searchQuery) { users, query ->
        if (query.isBlank()) users
        else users.filter { 
            it.username.contains(query, ignoreCase = true) || 
            it.email.contains(query, ignoreCase = true) 
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Notification counts for Red Dots
    val totalNotifications: StateFlow<Int> = combine(_friendRequests, _messageRequests) { friends, messages ->
        friends.size + messages.size
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            combine(
                chatRepository.getConversations(),
                groupRepository.getMyGroupConversations()
            ) { oneOnOneChats, customGroups ->
                (oneOnOneChats + customGroups).sortedByDescending { it.lastTimestamp }
            }.catch { e ->
                e.printStackTrace()
                _conversations.value = emptyList()
            }.collectLatest { _conversations.value = it }
        }
        viewModelScope.launch {
            chatRepository.getMessageRequests()
                .catch { _messageRequests.value = emptyList() }
                .collectLatest { _messageRequests.value = it }
        }
        viewModelScope.launch {
            chatRepository.getFriendRequests()
                .catch { _friendRequests.value = emptyList() }
                .collectLatest { _friendRequests.value = it }
        }
        viewModelScope.launch {
            chatRepository.getUsers()
                .catch { _allUsers.value = emptyList() }
                .collectLatest { _allUsers.value = it }
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    fun acceptFriend(userId: String) = viewModelScope.launch { chatRepository.acceptFriendRequest(userId) }
    fun rejectFriend(userId: String) = viewModelScope.launch { chatRepository.rejectFriendRequest(userId) }
    fun sendFriendRequest(userId: String) = viewModelScope.launch { chatRepository.sendFriendRequest(userId) }

    suspend fun logout() {
        authRepository.logout()
    }
}
