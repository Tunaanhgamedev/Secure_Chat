package com.example.securechat.presentation.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import coil.request.ImageRequest
import com.example.securechat.util.TimeUtils
import com.example.securechat.domain.model.Message
import com.example.securechat.domain.model.User
import com.example.securechat.presentation.home.AvatarCircle
import kotlinx.coroutines.launch

// Shared Colors for Meta style in ChatScreen
private val MessengerBlue = Color(0xFF0084FF)
private val DarkBackground = Color(0xFF1C1C1E)
private val SurfaceVariant = Color(0xFF2C2C2E)
private val SecondaryText = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    peerName: String,
    onNavigateBack: () -> Unit,
    onNavigateToCall: () -> Unit
) {
    val messages: List<Message> = viewModel.messages.collectAsState(initial = emptyList()).value
    val isFriend: Boolean = viewModel.isFriend.collectAsState(initial = false).value
    val peerUser: User? = viewModel.peerUser.collectAsState().value
    val inputTextState = remember { mutableStateOf<String>("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isUploading by viewModel.isUploading.collectAsState()
    val context = LocalContext.current

    val getFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            
            // Extract filename from URI
            var fileName = "attachment"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            viewModel.sendAttachment(uri, "Gửi tệp: $fileName", fileName, mimeType)
            coroutineScope.launch {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    val getImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.sendAttachment(uri, "Đã gửi một ảnh", "image_${System.currentTimeMillis()}.jpg", "image/jpeg")
            coroutineScope.launch {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Message Selection State for BottomSheet
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    val isPeerActuallyOnline = if (peerUser?.isPresenceHidden == true) false else (peerUser?.isOnline ?: false)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (showSheet && selectedMessage != null) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceVariant
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                if (selectedMessage!!.isMine && !selectedMessage!!.isDeletedForEveryone) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarCircle(name = peerName, url = peerUser?.photoUrl, size = 36, isOnline = isPeerActuallyOnline)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            val lastSeen = peerUser?.lastSeen
                            val statusText = if (isPeerActuallyOnline) {
                                "Đang hoạt động"
                            } else if (lastSeen != null && lastSeen > 0L) {
                                "Hoạt động ${TimeUtils.getRelativeTime(lastSeen)}"
                            } else {
                                "Ngoại tuyến"
                            }

                            Text(peerName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                            Text(
                                text = statusText,
                                color = if (isPeerActuallyOnline) Color(0xFF30D158) else Color(0xFF8E8E93),
                                fontSize = 11.sp
                            )
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
                        enabled = true
                    ) {
                        Icon(
                            Icons.Default.Call, 
                            contentDescription = "Gọi Video", 
                            tint = MessengerBlue
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
                IconButton(onClick = { 
                    getImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
                }) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = "Gửi ảnh", tint = MessengerBlue)
                }
                IconButton(onClick = { getFileLauncher.launch("*/*") }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Gửi tệp", tint = SecondaryText)
                }
                OutlinedTextField(
                    value = inputTextState.value,
                    onValueChange = { inputTextState.value = it },
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
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MessengerBlue)
                    Spacer(Modifier.width(12.dp))
                } else {
                    IconButton(
                        onClick = {
                            if (inputTextState.value.isNotBlank()) {
                                viewModel.sendMessage(inputTextState.value)
                                inputTextState.value = ""
                                coroutineScope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
                            }
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MessengerBlue)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi", tint = Color.White)
                    }
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
                items(messages, key = { it.id }) { msg -> 
                    MessengerBubble(
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
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessengerBubble(msg: Message, onLongClick: () -> Unit) {
    val alignment = if (msg.isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (msg.isDeletedForEveryone) {
        Color.Transparent
    } else if (msg.isMine) {
        MessengerBlue
    } else {
        SurfaceVariant
    }
    
    val textColor = if (msg.isDeletedForEveryone) SecondaryText else Color.White

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
                            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = Color.White)
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
                    style = if (msg.isDeletedForEveryone) TextStyle(
                        fontStyle = FontStyle.Italic
                    ) else TextStyle.Default
                )
            }
        }
    }
}
