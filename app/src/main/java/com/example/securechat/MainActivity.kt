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

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    private val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE_CAMERA,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
        )
    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            // Permission denied - you might want to show a Toast or Snackbar
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request Permissions if not granted
        if (!allPermissionsGranted()) {
            requestPermissionsLauncher.launch(permissions)
        }

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
                        // Pass intent extras to NavGraph for potential deep linking
                        val startDestination = intent.getStringExtra("navigate_to")
                        val targetId = intent.getStringExtra("target_id")
                        SecureChatNavGraph(
                            startDestinationOverride = startDestination,
                            targetIdOverride = targetId
                        )
                    }
                }
            }
        }
    }

    private fun allPermissionsGranted() = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}