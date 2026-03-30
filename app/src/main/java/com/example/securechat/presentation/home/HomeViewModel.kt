package com.example.securechat.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.domain.model.Conversation
import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.AuthRepository
import com.example.securechat.domain.usecase.GetConversationsUseCase
import com.example.securechat.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val chatRepository: ChatRepository, // still used for getUsers for simplicity
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredUsers: StateFlow<List<User>> = combine(_allUsers, _searchQuery) { users, query ->
        if (query.isBlank()) users
        else users.filter {
            it.username.contains(query, ignoreCase = true) ||
            it.email.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            try { getConversationsUseCase().collectLatest { _conversations.value = it } }
            catch (e: Exception) { e.printStackTrace() }
        }
        viewModelScope.launch {
            try { chatRepository.getUsers().collectLatest { _allUsers.value = it } }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
