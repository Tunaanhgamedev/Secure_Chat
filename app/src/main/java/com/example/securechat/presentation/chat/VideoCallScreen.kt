package com.example.securechat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun VideoCallScreen(
    onEndCall: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Remote Video Track Placeholder
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Remote Video Stream", color = Color.White)
        }

        // Local Video Track Placeholder
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(width = 100.dp, height = 150.dp)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text("Local", color = Color.White)
        }

        // Call Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            FloatingActionButton(
                onClick = onEndCall,
                containerColor = Color.Red,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "End Call")
            }
        }
    }
}
