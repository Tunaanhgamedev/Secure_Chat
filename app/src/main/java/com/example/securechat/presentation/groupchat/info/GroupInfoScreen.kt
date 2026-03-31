package com.example.securechat.presentation.groupchat.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.securechat.domain.model.User
import com.example.securechat.presentation.home.AvatarCircle
import com.example.securechat.presentation.home.DarkBackground
import com.example.securechat.presentation.home.MessengerBlue
import com.example.securechat.presentation.home.SecondaryText
import com.example.securechat.presentation.home.SurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    viewModel: GroupInfoViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit // When left group
) {
    val state by viewModel.uiState.collectAsState()
    var showAddMemberDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thông tin nhóm", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AvatarCircle(name = state.group?.name ?: "?", size = 80)
                    Spacer(Modifier.height(12.dp))
                    Text(state.group?.name ?: "Đang tải...", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("${state.membersInfo.size} thành viên", color = SecondaryText, fontSize = 14.sp)

                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.leaveGroup(onNavigateHome) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f), contentColor = Color.Red)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Rời nhóm")
                    }
                }
            }

            // Pending section (only Admin in Private group)
            if (state.isAdmin && state.group?.type == "private" && state.pendingInfo.isNotEmpty()) {
                item {
                    Text("Yêu cầu tham gia (${state.pendingInfo.size})", color = MessengerBlue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(state.pendingInfo) { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarCircle(name = user.username, url = user.photoUrl, size = 48)
                        Spacer(Modifier.width(16.dp))
                        Text(user.username, color = Color.White, modifier = Modifier.weight(1f))

                        IconButton(onClick = { viewModel.approveMember(user.id) }) {
                            Icon(Icons.Default.Check, contentDescription = "Duyệt", tint = Color(0xFF30D158))
                        }
                        IconButton(onClick = { viewModel.removeMemberOrRequest(user.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Từ chối", tint = Color.Red)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            // Add Member Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Thành viên (${state.membersInfo.size})", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showAddMemberDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MessengerBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Thêm", color = MessengerBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Members List
            items(state.membersInfo) { user ->
                val isMe = user.id == state.myUid
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarCircle(name = user.username, url = user.photoUrl, size = 48)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.username + if (isMe) " (Bạn)" else "", color = Color.White)
                        if (user.id == state.group?.adminId) {
                            Text("Quản trị viên", color = SecondaryText, fontSize = 12.sp)
                        }
                    }
                    if (state.isAdmin && !isMe) {
                        IconButton(onClick = { viewModel.removeMemberOrRequest(user.id) }) {
                            Icon(Icons.Default.RemoveCircle, contentDescription = "Xóa", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Thêm thành viên", color = Color.White) },
            text = {
                val availableFriends = state.allFriends.filter { friend ->
                    !state.membersInfo.any { it.id == friend.id } && !state.pendingInfo.any { it.id == friend.id }
                }
                if (availableFriends.isEmpty()) {
                    Text("Không có bạn bè nào để thêm.", color = SecondaryText)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(availableFriends) { friend ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    viewModel.addMember(friend.id)
                                    showAddMemberDialog = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarCircle(name = friend.username, url = friend.photoUrl, size = 40)
                                Spacer(Modifier.width(12.dp))
                                Text(friend.username, color = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text("Đóng", color = MessengerBlue)
                }
            },
            containerColor = SurfaceVariant
        )
    }

    state.error?.let {
        LaunchedEffect(it) {
            // Can show snackbar here later
            viewModel.clearError()
        }
    }
}
