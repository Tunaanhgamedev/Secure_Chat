package com.example.securechat.presentation.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.domain.model.User
import com.example.securechat.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val isReAuthenticated: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        viewModelScope.launch {
            authRepository.getCachedUser().collect { user ->
                _uiState.update { it.copy(user = user) }
            }
        }
    }

    fun reauthenticate(password: String) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        val result = authRepository.reauthenticate(password)
        if (result.isSuccess) {
            _uiState.update { it.copy(isLoading = false, isReAuthenticated = true, error = null) }
        } else {
            _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun updateProfile(username: String) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
        val result = authRepository.updateProfile(username, _uiState.value.user?.photoUrl)
        if (result.isSuccess) {
            _uiState.update { it.copy(isLoading = false, successMessage = "Cập nhật tên thành công!") }
        } else {
            _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun updateEmail(newEmail: String) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
        val result = authRepository.updateEmail(newEmail)
        if (result.isSuccess) {
            _uiState.update { it.copy(isLoading = false, successMessage = "Cập nhật Email thành công!") }
        } else {
            _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun updatePresence(isOnline: Boolean, isHidden: Boolean? = null) = viewModelScope.launch {
        authRepository.updatePresence(isOnline, isHidden)
    }

    fun updatePassword(newPassword: String) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
        val result = authRepository.updatePassword(newPassword)
        if (result.isSuccess) {
            _uiState.update { it.copy(isLoading = false, successMessage = "Đổi mật khẩu thành công!") }
        } else {
            _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun uploadAvatar(uri: Uri) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
        val uploadResult = authRepository.uploadAvatar(uri)
        if (uploadResult.isSuccess) {
            val url = uploadResult.getOrThrow()
            val updateResult = authRepository.updateProfile(_uiState.value.user?.username ?: "", url)
            if (updateResult.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Cập nhật ảnh đại diện thành công!") }
            } else {
                _uiState.update { it.copy(isLoading = false, error = updateResult.exceptionOrNull()?.message) }
            }
        } else {
            _uiState.update { it.copy(isLoading = false, error = uploadResult.exceptionOrNull()?.message) }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
