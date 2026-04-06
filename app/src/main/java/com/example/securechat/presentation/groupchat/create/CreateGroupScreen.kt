package com.example.securechat.presentation.groupchat.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.securechat.presentation.home.AvatarCircle
import com.example.securechat.presentation.home.DarkBackground
import com.example.securechat.presentation.home.MessengerBlue
import com.example.securechat.presentation.home.SurfaceVariant
import com.example.securechat.presentation.home.SecondaryText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    viewModel: CreateGroupViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onGroupCreated: (String) -> Unit // returns groupId
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.successGroupId) {
        state.successGroupId?.let { onGroupCreated(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tạo Nhóm Mới", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.createGroup() },
                        enabled = !state.isLoading && state.groupName.isNotBlank() && state.selectedMembers.isNotEmpty()
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(color = MessengerBlue, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Tạo", color = MessengerBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            OutlinedTextField(
                value = state.groupName,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text("Tên nhóm") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = SurfaceVariant,
                    focusedContainerColor = SurfaceVariant,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MessengerBlue,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = MessengerBlue,
                    unfocusedLabelColor = SecondaryText
                ),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

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
                        Text("Nhóm riêng tư", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Chỉ quản trị viên mới được duyệt thành viên.", color = SecondaryText, fontSize = 12.sp)
                    }
                    Switch(
                        checked = state.isPrivate,
                        onCheckedChange = { viewModel.onTypeChange(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MessengerBlue, checkedTrackColor = MessengerBlue.copy(alpha = 0.5f))
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Chọn thành viên", color = SecondaryText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))

            state.error?.let {
                Text(it, color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            if (state.friends.isEmpty()) {
                Text("Không có bạn bè để thêm.", color = SecondaryText, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.friends) { friend ->
                        val isSelected = state.selectedMembers.contains(friend.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleMember(friend.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarCircle(name = friend.username, url = friend.photoUrl, size = 48)
                            Spacer(Modifier.width(16.dp))
                            Text(friend.username, color = Color.White, modifier = Modifier.weight(1f), fontSize = 16.sp)

                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MessengerBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}
