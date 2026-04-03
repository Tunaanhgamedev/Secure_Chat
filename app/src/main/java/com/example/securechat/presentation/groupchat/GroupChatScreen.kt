package com.example.securechat.presentation.groupchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.securechat.presentation.home.AvatarCircle
import kotlinx.coroutines.launch

private val Primary  = Color(0xFF0A84FF)
private val BgDark   = Color(0xFF121212)
private val Surface1 = Color(0xFF1E1E1E)
private val Surface2 = Color(0xFF2C2C2E)
private val TextMain = Color(0xFFFFFFFF)
private val TextSub  = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GroupChatScreen(
    viewModel: GroupChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val messages       by viewModel.messages.collectAsState()
    var inputText      by remember { mutableStateOf("") }
    val listState      = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Selection State
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (showSheet && selectedMessage != null) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = Surface1
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                if (selectedMessage!!.isMine && !selectedMessage!!.isDeletedForEveryone) {
                    ListItem(
                        headlineContent = { Text("Thu hồi", color = Color.Red) },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Default.DeleteForever, contentDescription = null, tint = Color.Red) },
                        modifier = Modifier.androidx.compose.foundation.clickable {
                            viewModel.deleteMessage(selectedMessage!!.id, forEveryone = true)
                            showSheet = false
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                ListItem(
                    headlineContent = { Text("Gỡ ở phía bạn", color = TextMain) },
                    leadingContent = { Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = null, tint = TextMain) },
                    modifier = Modifier.androidx.compose.foundation.clickable {
                        viewModel.deleteMessage(selectedMessage!!.id, forEveryone = false)
                        showSheet = false
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nhóm Chat Chung", fontWeight = FontWeight.Bold, color = TextMain, fontSize = 17.sp)
                        Text("Tất cả mọi người", color = TextSub, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface1)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(Surface1).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Nhắn tin đến nhóm…", color = TextSub) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Primary,
                        unfocusedBorderColor    = Surface2,
                        focusedTextColor        = TextMain,
                        unfocusedTextColor      = TextMain,
                        cursorColor             = Primary,
                        focusedContainerColor   = Surface2,
                        unfocusedContainerColor = Surface2
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
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(Primary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi", tint = Color.White)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(messages, key = { it.id }) { msg -> 
                GroupMessageBubble(
                    msg = msg,
                    onLongClick = {
                        selectedMessage = msg
                        showSheet = true
                    }
                ) 
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GroupMessageBubble(msg: Message, onLongClick: () -> Unit) {
    val bubbleColor = if (msg.isDeletedForEveryone) {
        Color.Transparent
    } else if (msg.isMine) {
        Primary
    } else {
        Surface2
    }
    
    val textColor = if (msg.isDeletedForEveryone) TextSub else TextMain

    if (msg.isMine) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(bubbleColor)
                    .androidx.compose.foundation.border(
                        width = if (msg.isDeletedForEveryone) 1.dp else 0.dp,
                        color = if (msg.isDeletedForEveryone) Surface2 else Color.Transparent,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = if (!msg.isDeletedForEveryone) onLongClick else null
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 260.dp)
            ) {
                Text(
                    text = msg.content, 
                    color = textColor, 
                    fontSize = 15.sp,
                    style = if (msg.isDeletedForEveryone) androidx.compose.ui.text.TextStyle(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ) else androidx.compose.ui.text.TextStyle.Default
                )
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
            AvatarCircle(name = msg.senderName.ifBlank { "?" }, size = 32)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(msg.senderName, color = TextSub, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                        .background(bubbleColor)
                        .androidx.compose.foundation.border(
                            width = if (msg.isDeletedForEveryone) 1.dp else 0.dp,
                            color = if (msg.isDeletedForEveryone) Surface2 else Color.Transparent,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onLongClick
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .widthIn(max = 260.dp)
                ) {
                    Text(
                        text = msg.content, 
                        color = textColor, 
                        fontSize = 15.sp,
                        style = if (msg.isDeletedForEveryone) androidx.compose.ui.text.TextStyle(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ) else androidx.compose.ui.text.TextStyle.Default
                    )
                }
            }
        }
    }
}

