package com.example.securechat.presentation.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
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
import com.example.securechat.domain.model.Message
import com.example.securechat.presentation.home.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    peerName: String,
    onNavigateBack: () -> Unit,
    onNavigateToCall: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isFriend by viewModel.isFriend.collectAsState()
    val peerUser by viewModel.peerUser.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isPeerActuallyOnline = if (peerUser?.isPresenceHidden == true) false else (peerUser?.isOnline ?: false)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarCircle(name = peerName, url = peerUser?.photoUrl, size = 36, isOnline = isPeerActuallyOnline)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            val statusText = if (isPeerActuallyOnline) "Đang hoạt động"
                            else if (peerUser?.lastSeen != null && peerUser?.lastSeen!! > 0L) "Hoạt động ${com.example.securechat.util.TimeUtils.getRelativeTime(peerUser?.lastSeen!!)}"
                            else "Ngoại tuyến"

                            Text(peerName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                            Text(statusText, color = if (isPeerActuallyOnline) Color(0xFF30D158) else SecondaryText, fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = MessengerBlue)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToCall,
                        enabled = isFriend
                    ) {
                        Icon(
                            Icons.Default.Call, 
                            contentDescription = "Gọi Video", 
                            tint = if (isFriend) MessengerBlue else SecondaryText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceVariant)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Nhắn tin…", color = SecondaryText) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = MessengerBlue,
                        unfocusedBorderColor    = SurfaceVariant,
                        focusedTextColor        = Color.White,
                        unfocusedTextColor      = Color.White,
                        cursorColor             = MessengerBlue,
                        focusedContainerColor   = DarkBackground,
                        unfocusedContainerColor = DarkBackground
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                            coroutineScope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
                        }
                    },
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(MessengerBlue)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi", tint = Color.White)
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            
            // Friend Request Banner
            AnimatedVisibility(visible = !isFriend) {
                Surface(
                    color = SurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MessengerBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chưa là bạn bè", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Tin nhắn sẽ được gửi vào mục Tin nhắn chờ.", color = SecondaryText, fontSize = 12.sp)
                        }
                        TextButton(onClick = { viewModel.sendFriendRequest() }) {
                            Text("Thêm bạn", color = MessengerBlue)
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(messages, key = { it.id }) { msg -> MessengerBubble(msg) }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
fun MessengerBubble(msg: Message) {
    val alignment = if (msg.isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (msg.isMine) MessengerBlue else SurfaceVariant
    val textColor = Color.White

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart    = 18.dp,
                        topEnd      = 18.dp,
                        bottomStart = if (msg.isMine) 18.dp else 4.dp,
                        bottomEnd   = if (msg.isMine) 4.dp else 18.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 260.dp)
        ) {
            Text(text = msg.content, color = textColor, fontSize = 15.sp)
        }
    }
}
