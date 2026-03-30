package com.example.securechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.securechat.domain.repository.AuthRepository
import com.example.securechat.presentation.navigation.SecureChatNavGraph
import com.example.securechat.ui.theme.SecureChatTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Track App Lifecycle for Presence
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                try {
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                authRepository.updatePresence(isOnline = true)
                            }
                        }
                        Lifecycle.Event.ON_STOP -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                authRepository.updatePresence(isOnline = false)
                            }
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    // Fail silently to prevent app crash
                }
            }
        })

        enableEdgeToEdge()
        setContent {
            SecureChatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
                        SecureChatNavGraph()
                    }
                }
            }
        }
    }
}