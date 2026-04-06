package com.example.securechat.presentation.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.webrtc.EglBase
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.securechat.data.remote.WebRtcClient
import com.example.securechat.presentation.home.AvatarCircle
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun VideoCallScreen(
    viewModel: VideoCallViewModel = hiltViewModel(),
    onEndCall: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val remoteTrack by viewModel.remoteVideoTrack.collectAsState()
    val networkMessage by viewModel.networkMessage.collectAsState()

    LaunchedEffect(callState) {
        if (callState == CallState.ENDED) {
            onEndCall()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
        when (callState) {
            CallState.CALLING, CallState.RINGING -> {
                CallingUI(viewModel.peerName, viewModel.eglContext, viewModel)
            }
            CallState.CONNECTED -> {
                VideoUI(remoteTrack, viewModel.eglContext, viewModel)
            }
            else -> {}
        }

        // Overlay Message (Messenger-like)
        networkMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = msg, color = Color.White, fontSize = 14.sp)
            }
        }

        // Common Controls at bottom
        CallControls(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            onEndCall = { viewModel.endCall(); onEndCall() },
            onSwitchCamera = { viewModel.switchCamera() }
        )
    }
}

@Composable
fun CallingUI(name: String, eglContext: EglBase.Context, viewModel: VideoCallViewModel) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Full screen background for local preview while calling
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    init(eglContext, null)
                    setEnableHardwareScaler(true)
                    setMirror(true)
                }
            },
            update = { view ->
                viewModel.getLocalVideoTrack().addSink(view)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Dark overlay for readability
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Pulse effect
                Box(
                    modifier = Modifier
                        .size((120 * pulseScale).dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A84FF).copy(alpha = 0.2f))
                )
                AvatarCircle(name = name, size = 120)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Đang gọi...", color = Color(0xFFE5E5EA), fontSize = 16.sp)
        }
    }
}

@Composable
fun VideoUI(remoteTrack: VideoTrack?, eglContext: EglBase.Context, viewModel: VideoCallViewModel) {
    // Large Remote View
    Box(modifier = Modifier.fillMaxSize()) {
        if (remoteTrack != null) {
            WebRTCVideoView(videoTrack = remoteTrack, eglContext = eglContext, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF0A84FF))
                    Spacer(Modifier.height(8.dp))
                    Text("Đang kết nối tín hiệu...", color = Color.White, fontSize = 14.sp)
                }
            }
        }
        
        // Small floating local view (Top Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(width = 120.dp, height = 180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        init(eglContext, null)
                        setEnableHardwareScaler(true)
                        setMirror(true)
                    }
                },
                update = { view ->
                    viewModel.getLocalVideoTrack().addSink(view)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun WebRTCVideoView(videoTrack: VideoTrack, eglContext: EglBase.Context, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglContext, null)
                setEnableHardwareScaler(true)
            }
        },
        update = { view ->
            videoTrack.addSink(view)
        },
        modifier = modifier
    )
}

@Composable
fun CallControls(
    modifier: Modifier = Modifier, 
    onEndCall: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    var isMicOn by remember { mutableStateOf(true) }
    var isVideoOn by remember { mutableStateOf(true) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { isMicOn = !isMicOn },
            modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isMicOn) Color.White.copy(0.1f) else Color.White)
        ) {
            Icon(if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff, contentDescription = null, tint = if (isMicOn) Color.White else Color.Black)
        }

        IconButton(
            onClick = onSwitchCamera,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(0.1f))
        ) {
            Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Đảo Camera", tint = Color.White)
        }

        FloatingActionButton(
            onClick = onEndCall,
            containerColor = Color(0xFFFF3B30), // System Red
            contentColor = Color.White,
            modifier = Modifier.size(72.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = "End Call", modifier = Modifier.size(32.dp))
        }

        IconButton(
            onClick = { isVideoOn = !isVideoOn },
            modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isVideoOn) Color.White.copy(0.1f) else Color.White)
        ) {
            Icon(if (isVideoOn) Icons.Default.Videocam else Icons.Default.VideocamOff, contentDescription = null, tint = if (isVideoOn) Color.White else Color.Black)
        }
    }
}
