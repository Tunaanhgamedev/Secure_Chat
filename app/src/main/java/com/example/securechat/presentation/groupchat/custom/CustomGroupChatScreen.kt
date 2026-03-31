package com.example.securechat.presentation.groupchat.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.securechat.presentation.home.DarkBackground
import com.example.securechat.presentation.home.MessengerBlue
import com.example.securechat.presentation.home.SecondaryText
import com.example.securechat.presentation.home.SurfaceVariant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomGroupChatScreen(
    viewModel: CustomGroupChatViewModel = hiltViewModel(),
    groupNameArg: String,
    onNavigateBack: () -> Unit,
    onNavigateToGroupInfo: (String) -> Unit // pass groupId
) {
    val messages by viewModel.messages.collectAsState()
    val groupInfo by viewModel.groupInfo.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Title from DB or Argument
    val displayTitle = groupInfo?.name ?: groupNameArg

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            groupInfo?.id?.let { onNavigateToGroupInfo(it) }
                        }
                    ) {
                        Text(displayTitle, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                        val memberCount = groupInfo?.members?.size ?: 0
                        Text("$memberCount thành viên", color = SecondaryText, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = MessengerBlue)
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
                    placeholder = { Text("Nhắn tin đến nhóm…", color = SecondaryText) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MessengerBlue,
                        unfocusedBorderColor = SurfaceVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = MessengerBlue,
                        focusedContainerColor = SurfaceVariant,
                        unfocusedContainerColor = SurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                            coroutineScope.launch {
                                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(MessengerBlue)
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
            items(messages, key = { it.id }) { msg -> GroupMessageBubble(msg) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun GroupMessageBubble(msg: Message) {
    if (msg.isMine) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(MessengerBlue).padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 260.dp)
            ) {
                Text(msg.content, color = Color.White, fontSize = 15.sp)
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
            AvatarCircle(name = msg.senderName.ifBlank { "?" }, size = 32)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(msg.senderName, color = SecondaryText, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                        .background(SurfaceVariant).padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 260.dp)
                ) {
                    Text(msg.content, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}
