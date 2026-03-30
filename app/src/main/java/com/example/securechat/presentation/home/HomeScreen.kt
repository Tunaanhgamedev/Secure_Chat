package com.example.securechat.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.securechat.domain.model.Conversation
import com.example.securechat.domain.model.User
import java.text.SimpleDateFormat
import java.util.*

private val Primary  = Color(0xFF0A84FF)
private val BgDark   = Color(0xFF121212)
private val Surface1 = Color(0xFF1E1E1E)
private val Surface2 = Color(0xFF2A2A2A)
private val TextMain = Color(0xFFFFFFFF)
private val TextSub  = Color(0xFF8E8E93)

enum class HomeTab { MESSAGES, FIND_FRIENDS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onConversationClick: (peerId: String, peerName: String) -> Unit,
    onUserClick: (User) -> Unit,
    onGroupChatClick: () -> Unit,
    onLogout: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val filteredUsers by viewModel.filteredUsers.collectAsState()
    val searchQuery   by viewModel.searchQuery.collectAsState()
    var selectedTab   by remember { mutableStateOf(HomeTab.MESSAGES) }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text("SecureChat", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Primary)
                    },
                    actions = {
                        IconButton(onClick = onGroupChatClick) {
                            Icon(Icons.Default.Groups, contentDescription = "Nhóm Chat", tint = Primary)
                        }
                        IconButton(onClick = { viewModel.logout(); onLogout() }) {
                            Icon(Icons.Default.Logout, contentDescription = "Đăng xuất", tint = TextSub)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
                )
                AnimatedVisibility(visible = selectedTab == HomeTab.FIND_FRIENDS) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        placeholder = { Text("Tìm kiếm theo tên hoặc email…", color = TextSub) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSub) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Primary,
                            unfocusedBorderColor    = Surface2,
                            focusedTextColor        = TextMain,
                            unfocusedTextColor      = TextMain,
                            cursorColor             = Primary,
                            focusedContainerColor   = Surface1,
                            unfocusedContainerColor = Surface1
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Surface1) {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.MESSAGES,
                    onClick  = { selectedTab = HomeTab.MESSAGES },
                    icon     = { Icon(Icons.Default.ChatBubble, contentDescription = null) },
                    label    = { Text("Tin Nhắn") },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = Primary, selectedTextColor   = Primary,
                        unselectedIconColor = TextSub,  unselectedTextColor = TextSub,
                        indicatorColor      = Surface2
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.FIND_FRIENDS,
                    onClick  = { selectedTab = HomeTab.FIND_FRIENDS },
                    icon     = { Icon(Icons.Default.PersonSearch, contentDescription = null) },
                    label    = { Text("Tìm Bạn") },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = Primary, selectedTextColor   = Primary,
                        unselectedIconColor = TextSub,  unselectedTextColor = TextSub,
                        indicatorColor      = Surface2
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(BgDark)) {
            when (selectedTab) {
                HomeTab.MESSAGES     -> ConversationsTab(conversations, onConversationClick)
                HomeTab.FIND_FRIENDS -> FindFriendsTab(filteredUsers, onUserClick)
            }
        }
    }
}

// ─── Tab: Hội thoại ─────────────────────────────────────────────────────────────
@Composable
private fun ConversationsTab(
    conversations: List<Conversation>,
    onConversationClick: (peerId: String, peerName: String) -> Unit
) {
    if (conversations.isEmpty()) {
        EmptyState(Icons.Default.ChatBubbleOutline, "Chưa có tin nhắn nào.\nHãy sang tab Tìm Bạn để bắt đầu chat!")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(conversations, key = { it.peerId }) { conv ->
                ConversationItem(conv) { onConversationClick(conv.peerId, conv.peerName) }
                HorizontalDivider(color = Surface2, modifier = Modifier.padding(start = 80.dp))
            }
        }
    }
}

@Composable
private fun ConversationItem(conv: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(name = conv.peerName.ifBlank { conv.peerEmail }, size = 52)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(conv.peerName.ifBlank { conv.peerEmail }, fontWeight = FontWeight.SemiBold, color = TextMain, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(conv.lastMessage, color = TextSub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(formatTime(conv.lastTimestamp), color = TextSub, fontSize = 12.sp)
    }
}

// ─── Tab: Tìm bạn ───────────────────────────────────────────────────────────────
@Composable
private fun FindFriendsTab(users: List<User>, onUserClick: (User) -> Unit) {
    if (users.isEmpty()) {
        EmptyState(Icons.Default.PersonSearch, "Không tìm thấy người dùng nào.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(users, key = { it.id }) { user ->
                UserItem(user) { onUserClick(user) }
                HorizontalDivider(color = Surface2, modifier = Modifier.padding(start = 80.dp))
            }
        }
    }
}

@Composable
fun UserItem(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(name = user.username.ifBlank { user.email }, size = 52)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(user.username.ifBlank { user.email }, fontWeight = FontWeight.SemiBold, color = TextMain, fontSize = 16.sp)
            Text(user.email, color = TextSub, fontSize = 13.sp)
        }
    }
}

// ─── Shared ──────────────────────────────────────────────────────────────────────
@Composable
fun AvatarCircle(name: String, size: Int) {
    val letters = name.trim().split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
    val palette = listOf(Color(0xFF0A84FF), Color(0xFF30D158), Color(0xFFFF9F0A), Color(0xFFFF375F), Color(0xFF64D2FF), Color(0xFFBF5AF2))
    val bg = palette[name.length % palette.size]
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(letters.ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.36f).sp)
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = TextSub, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = TextSub, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
    }
}

private fun formatTime(ts: Long): String {
    if (ts == 0L) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000     -> "Vừa xong"
        diff < 3_600_000  -> "${diff / 60_000} phút"
        diff < 86_400_000 -> "${diff / 3_600_000} giờ"
        else              -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(ts))
    }
}
