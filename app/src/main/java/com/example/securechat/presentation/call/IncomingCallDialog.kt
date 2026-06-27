package com.example.securechat.presentation.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.securechat.domain.model.IncomingCallModel
import com.example.securechat.presentation.home.AvatarCircle
import com.example.securechat.presentation.home.DarkBackground
import com.example.securechat.presentation.home.SecondaryText

@Composable
fun IncomingCallDialog(
    callModel: IncomingCallModel,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = DarkBackground,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Cuộc gọi đến", color = SecondaryText, fontSize = 16.sp, modifier = Modifier.padding(bottom = 24.dp))
                
                AvatarCircle(name = callModel.callerName, url = callModel.callerPhotoUrl, size = 100)
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = callModel.callerName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text("Video Call", color = SecondaryText, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                
                Spacer(Modifier.height(48.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Decline Button
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Từ chối", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    
                    // Accept Button
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF30D158))
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Nghe", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}
