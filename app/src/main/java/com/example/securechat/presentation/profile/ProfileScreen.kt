package com.example.securechat.presentation.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.securechat.presentation.home.AvatarCircle
import com.example.securechat.presentation.home.DarkBackground
import com.example.securechat.presentation.home.MessengerBlue
import com.example.securechat.presentation.home.SurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var name by remember(uiState.user) { mutableStateOf(uiState.user?.username ?: "") }
    var email by remember(uiState.user) { mutableStateOf(uiState.user?.email ?: "") }
    var password by remember { mutableStateOf("") }
    
    var showReauthDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.uploadAvatar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chỉnh sửa trang cá nhân", color = Color.White, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar with Picker
            Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.padding(vertical = 24.dp)) {
                Box {
                    AvatarCircle(name = name, url = uiState.user?.photoUrl, size = 120)
                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier.matchParentSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        }
                    }
                }
                IconButton(
                    onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(MessengerBlue)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Họ và tên") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (Cần xác thực lại)") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mật khẩu mới (Cần xác thực lại)") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Incognito Mode Switch
            Surface(
                color = SurfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Trạng thái hoạt động", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Cho người khác biết khi bạn đang online.", color = Color(0xFF8E8E93), fontSize = 12.sp)
                    }
                    Switch(
                        checked = !(uiState.user?.isPresenceHidden ?: false),
                        onCheckedChange = { isVisible ->
                            viewModel.updatePresence(isOnline = isVisible, isHidden = !isVisible)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = MessengerBlue, checkedTrackColor = MessengerBlue.copy(alpha = 0.5f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (email != uiState.user?.email || password.isNotEmpty()) {
                        pendingAction = {
                            if (email != uiState.user?.email) viewModel.updateEmail(email)
                            if (password.isNotEmpty()) viewModel.updatePassword(password)
                            if (name != uiState.user?.username) viewModel.updateProfile(name)
                        }
                        showReauthDialog = true
                    } else if (name != uiState.user?.username) {
                        viewModel.updateProfile(name)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue),
                shape = MaterialTheme.shapes.medium
            ) {
                if (uiState.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Lưu thay đổi", fontWeight = FontWeight.Bold)
            }

            uiState.error?.let { Text(it, color = Color.Red, modifier = Modifier.padding(top = 16.dp)) }
            uiState.successMessage?.let { Text(it, color = Color.Green, modifier = Modifier.padding(top = 16.dp)) }
        }
    }

    if (showReauthDialog) {
        ReauthDialog(
            onConfirm = { oldPass ->
                showReauthDialog = false
                viewModel.reauthenticate(oldPass)
            },
            onDismiss = { showReauthDialog = false },
            isLoading = uiState.isLoading
        )
    }

    // After re-auth succeeds, perform the pending action
    LaunchedEffect(uiState.isReAuthenticated) {
        if (uiState.isReAuthenticated) {
            pendingAction?.invoke()
            pendingAction = null
        }
    }
}

@Composable
fun ReauthDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit, isLoading: Boolean) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xác thực lại") },
        text = {
            Column {
                Text("Vui lòng nhập mật khẩu hiện tại để xác nhận thay đổi quan trọng.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mật khẩu hiện tại") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Xác nhận")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        },
        containerColor = SurfaceVariant,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = SurfaceVariant,
    focusedContainerColor = SurfaceVariant,
    unfocusedBorderColor = Color.Transparent,
    focusedBorderColor = MessengerBlue,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = MessengerBlue,
    unfocusedLabelColor = Color(0xFF8E8E93)
)
