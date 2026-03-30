package com.example.securechat.presentation.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.securechat.domain.model.Conversation
import com.example.securechat.domain.model.User
import kotlinx.coroutines.launch

// Custom Colors for Meta/Messenger style
val MessengerBlue = Color(0xFF0084FF)
val DarkBackground = Color(0xFF1C1C1E)
val SurfaceVariant = Color(0xFF2C2C2E)
val SecondaryText = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onConversationClick: (String, String) -> Unit,
    onUserClick: (User) -> Unit,
    onGroupChatClick: () -> Unit,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val filteredUsers by viewModel.filteredUsers.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val friendRequests by viewModel.friendRequests.collectAsState()
    val messageRequests by viewModel.messageRequests.collectAsState()
    val totalNotifications by viewModel.totalNotifications.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(HomeTab.MESSAGES) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkBackground,
                modifier = Modifier.width(300.dp)
            ) {
                DrawerContent(
                    user     = currentUser,
                    notifCount = totalNotifications,
                    friendRequestCount = friendRequests.size,
                    messageRequestCount = messageRequests.size,
                    onEditProfile = onProfileClick,
                    onSettings = onProfileClick,
                    onTabSelect = { tab ->
                        selectedTab = tab
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SecureChat", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        Box {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                            }
                            if (totalNotifications > 0) {
                                Badge(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onGroupChatClick) {
                            Icon(Icons.Default.Groups, contentDescription = "Groups", tint = Color.White)
                        }
                        IconButton(onClick = { viewModel.logout(); onLogout() }) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = DarkBackground) {
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.MESSAGES,
                        onClick = { selectedTab = HomeTab.MESSAGES },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                        label = { Text("Tin Nhắn") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MessengerBlue, selectedTextColor = MessengerBlue, unselectedIconColor = SecondaryText)
                    )
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.FIND_FRIENDS,
                        onClick = { selectedTab = HomeTab.FIND_FRIENDS },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Tìm Bạn") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MessengerBlue, selectedTextColor = MessengerBlue, unselectedIconColor = SecondaryText)
                    )
                }
            },
            containerColor = DarkBackground
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Search Bar in Find Friends
                if (selectedTab == HomeTab.FIND_FRIENDS) {
                    SearchBar(searchQuery, viewModel::onSearchQueryChange)
                }

                when (selectedTab) {
                    HomeTab.MESSAGES -> ConversationsTab(conversations, onConversationClick)
                    HomeTab.FIND_FRIENDS -> FindFriendsTab(filteredUsers, onUserClick)
                    HomeTab.REQUESTS -> FriendRequestsTab(friendRequests, viewModel::acceptFriend, viewModel::rejectFriend)
                }
            }
        }
    }
}

@Composable
fun DrawerContent(
    user: User?,
    notifCount: Int,
    friendRequestCount: Int,
    messageRequestCount: Int,
    onEditProfile: () -> Unit,
    onSettings: () -> Unit,
    onTabSelect: (HomeTab) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
            Text("Menu", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White)
        }

        // Profile Section
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onEditProfile() }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCircle(name = user?.username ?: "?", url = user?.photoUrl, size = 56)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(user?.username ?: "Người dùng", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Chuyển trang cá nhân", color = SecondaryText, fontSize = 13.sp)
            }
        }

        DrawerItem(Icons.Default.Settings, "Cài đặt", onClick = onSettings)
        
        HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))

        DrawerItem(Icons.Default.Group, "Cộng đồng", badge = "Mới")
        DrawerItem(Icons.Default.Forum, "Tin nhắn đang chờ", count = messageRequestCount, onClick = { /* Pending chat tab */ })
        DrawerItem(Icons.Default.Archive, "Kho lưu trữ")

        HorizontalDivider(color = SurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
        
        DrawerItem(Icons.Default.PersonAdd, "Lời mời kết bạn", count = friendRequestCount, onClick = { onTabSelect(HomeTab.REQUESTS) })
        DrawerItem(Icons.Default.Checklist, "Lời mời tham gia kênh", hasDot = true)

        Spacer(modifier = Modifier.weight(1f))
        
        Text("Cũng của Meta", color = SecondaryText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
        DrawerItem(Icons.Default.PlayCircleOutline, "Facebook Reels")
        DrawerItem(Icons.Default.Event, "Sự kiện trên Facebook")
    }
}

@Composable
fun DrawerItem(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    count: Int = 0,
    hasDot: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        
        if (badge != null) {
            Box(Modifier.background(MessengerBlue, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (count > 0) {
            Box(Modifier.size(20.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) {
                Text(count.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (hasDot) {
            Box(Modifier.size(8.dp).background(MessengerBlue, CircleShape))
        }
    }
}

@Composable
fun SearchBar(query: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onValueChange,
        placeholder = { Text("Tìm kiếm theo tên hoặc email…", color = SecondaryText) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = SecondaryText) },
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = SurfaceVariant,
            focusedContainerColor = SurfaceVariant,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            cursorColor = MessengerBlue,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        singleLine = true
    )
}

@Composable
fun AvatarCircle(name: String, url: String? = null, size: Int = 40, isOnline: Boolean = false) {
    Box(contentAlignment = Alignment.BottomEnd) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model      = url,
                contentDescription = null,
                modifier = Modifier.size(size.dp).clip(CircleShape).background(SurfaceVariant),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(MessengerBlue),
                contentAlignment = Alignment.Center
            ) {
                val initial = name.firstOrNull()?.uppercase() ?: "?"
                Text(text = initial, color = Color.White, fontSize = (size / 2).sp, fontWeight = FontWeight.Bold)
            }
        }
        
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size((size / 4).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF30D158))
                    .padding(2.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF30D158)))
            }
        }
    }
}

@Composable
fun ConversationsTab(conversations: List<Conversation>, onClick: (String, String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(conversations) { convo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(convo.peerId, convo.peerName) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCircle(name = convo.peerName, url = null, size = 56, isOnline = convo.isOnline)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = convo.peerName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (convo.isOnline) "Đang hoạt động" else com.example.securechat.util.TimeUtils.getRelativeTime(convo.lastSeen),
                            color = if (convo.isOnline) Color(0xFF30D158) else SecondaryText,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = "• ${convo.lastMessage}", color = SecondaryText, maxLines = 1, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FindFriendsTab(users: List<User>, onUserClick: (User) -> Unit) {
    if (users.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(64.dp), tint = SecondaryText)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Không tìm thấy người dùng nào.", color = SecondaryText)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(users) { user ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onUserClick(user) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isActuallyOnline = if (user.isPresenceHidden) false else user.isOnline
                    AvatarCircle(name = user.username, url = user.photoUrl, size = 56, isOnline = isActuallyOnline)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = user.username, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isActuallyOnline) "Đang hoạt động" else "Hoạt động ${com.example.securechat.util.TimeUtils.getRelativeTime(user.lastSeen)}",
                            color = if (isActuallyOnline) Color(0xFF30D158) else SecondaryText, 
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = { onUserClick(user) },
                        colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue)
                    ) {
                        Text("Nhắn tin", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun FriendRequestsTab(requests: List<User>, onAccept: (String) -> Unit, onReject: (String) -> Unit) {
    if (requests.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Không có lời mời kết bạn nào.", color = SecondaryText)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(requests) { user ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarCircle(name = user.username, url = user.photoUrl, size = 56)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = user.username, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            Button(onClick = { onAccept(user.id) }, colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue)) {
                                Text("Chấp nhận")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { onReject(user.id) }, colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)) {
                                Text("Xóa", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
