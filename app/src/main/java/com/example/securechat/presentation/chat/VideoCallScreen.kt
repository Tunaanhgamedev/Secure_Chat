package com.example.securechat.presentation.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    LaunchedEffect(callState) {
        if (callState == CallState.ENDED) {
            onEndCall()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
        when (callState) {
            CallState.CALLING, CallState.RINGING -> {
                CallingUI(viewModel.peerName)
            }
            CallState.CONNECTED -> {
                VideoUI(remoteTrack)
            }
            else -> {}
        }

        // Common Controls at bottom
        CallControls(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            onEndCall = { viewModel.endCall(); onEndCall() }
        )
    }
}

@Composable
fun CallingUI(name: String) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

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
        Text(text = "Đang gọi...", color = Color(0xFF8E8E93), fontSize = 16.sp)
    }
}

@Composable
fun VideoUI(remoteTrack: VideoTrack?) {
    // Large Remote View
    Box(modifier = Modifier.fillMaxSize()) {
        if (remoteTrack != null) {
            WebRTCVideoView(videoTrack = remoteTrack, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF0A84FF))
            }
        }
        
        // Small floating local view
        // In a real app, we'd add local view here using surfaceViewRenderer
    }
}

@Composable
fun WebRTCVideoView(videoTrack: VideoTrack, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                // We'd need eglBaseContext from webRtcClient. This is tricky with DI in factory.
                // For now, simplify or assume it's initialized.
            }
        },
        update = { view ->
            videoTrack.addSink(view)
        },
        modifier = modifier
    )
}

@Composable
fun CallControls(modifier: Modifier = Modifier, onEndCall: () -> Unit) {
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
