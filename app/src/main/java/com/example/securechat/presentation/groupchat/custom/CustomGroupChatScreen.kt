package com.example.securechat.presentation.groupchat.custom

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.securechat.domain.model.Message
import com.example.securechat.presentation.home.AvatarCircle
import com.example.securechat.presentation.home.DarkBackground
import com.example.securechat.presentation.home.MessengerBlue
import com.example.securechat.presentation.home.SecondaryText
import com.example.securechat.presentation.home.SurfaceVariant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CustomGroupChatScreen(
    viewModel: CustomGroupChatViewModel = hiltViewModel(),
    groupNameArg: String,
    onNavigateBack: () -> Unit,
    onNavigateToGroupInfo: (String) -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val groupInfo by viewModel.groupInfo.collectAsState()
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isUploading by viewModel.isUploading.collectAsState()
    val context = LocalContext.current

    val getFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            
            var fileName = "attachment"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            viewModel.sendAttachment(uri, "Tệp đính kèm: $fileName", fileName, mimeType)
            coroutineScope.launch {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Selection State
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    val displayTitle = groupInfo?.name ?: groupNameArg
    val isAdmin = groupInfo?.adminId == myUid

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showSheet && selectedMessage != null) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceVariant
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                // Sender OR Admin can revoke
                val canRevoke = selectedMessage!!.senderId == myUid || isAdmin
                if (canRevoke && !selectedMessage!!.isDeletedForEveryone) {
                    ListItem(
                        headlineContent = { Text("Thu hồi", color = Color.Red) },
                        leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.Red) },
                        modifier = Modifier.clickable {
                            viewModel.deleteMessage(selectedMessage!!.id, forEveryone = true)
                            showSheet = false
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                ListItem(
                    headlineContent = { Text("Gỡ ở phía bạn", color = Color.White) },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White) },
                    modifier = Modifier.clickable {
                        viewModel.deleteMessage(selectedMessage!!.id, forEveryone = false)
                        showSheet = false
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
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
                IconButton(onClick = { getFileLauncher.launch("*/*") }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Gửi tệp", tint = SecondaryText)
                }
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
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MessengerBlue)
                    Spacer(Modifier.width(12.dp))
                } else {
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
        MessengerBlue
    } else {
        SurfaceVariant
    }
    
    val textColor = if (msg.isDeletedForEveryone) SecondaryText else Color.White

    if (msg.isMine) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(bubbleColor)
                    .border(
                        width = if (msg.isDeletedForEveryone) 1.dp else 0.dp,
                        color = if (msg.isDeletedForEveryone) SurfaceVariant else Color.Transparent,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = if (!msg.isDeletedForEveryone) onLongClick else null
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 260.dp)
            ) {
                Column {
                    if (!msg.isDeletedForEveryone && msg.fileUrl != null) {
                        val context = LocalContext.current
                        if (msg.fileType?.startsWith("image/") == true) {
                            AsyncImage(
                                model = msg.fileUrl,
                                contentDescription = msg.fileName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(msg.fileUrl))
                                        context.startActivity(intent)
                                    }
                            )
                            Spacer(Modifier.height(4.dp))
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically, 
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(msg.fileUrl))
                                        context.startActivity(intent)
                                    }
                                    .padding(8.dp)
                            ) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(msg.fileName ?: "Tệp", color = Color.White, fontSize = 12.sp, maxLines = 1)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
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
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
            AvatarCircle(name = msg.senderName.ifBlank { "?" }, size = 32)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(msg.senderName, color = SecondaryText, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                        .background(bubbleColor)
                        .border(
                            width = if (msg.isDeletedForEveryone) 1.dp else 0.dp,
                            color = if (msg.isDeletedForEveryone) SurfaceVariant else Color.Transparent,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onLongClick // For non-mine messages, Admin can long click to revoke, or User long click to hide for self.
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .widthIn(max = 260.dp)
                ) {
                    Column {
                        if (!msg.isDeletedForEveryone && msg.fileUrl != null) {
                            val context = LocalContext.current
                            if (msg.fileType?.startsWith("image/") == true) {
                                AsyncImage(
                                    model = msg.fileUrl,
                                    contentDescription = msg.fileName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(msg.fileUrl))
                                            context.startActivity(intent)
                                        }
                                )
                                Spacer(Modifier.height(4.dp))
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically, 
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(msg.fileUrl))
                                            context.startActivity(intent)
                                        }
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text(msg.fileName ?: "Tệp", color = Color.White, fontSize = 12.sp, maxLines = 1)
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
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
}

