package com.example.securechat.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.securechat.presentation.chat.ChatScreen
import com.example.securechat.presentation.chat.VideoCallScreen
import com.example.securechat.presentation.login.LoginScreen
import com.example.securechat.presentation.register.RegisterScreen
import com.example.securechat.presentation.home.UserListScreen
import com.google.firebase.auth.FirebaseAuth

import androidx.compose.runtime.remember

@Composable
fun SecureChatNavGraph() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val startDestination = remember { if (auth.currentUser != null) "home" else "login" }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                onNavigateToRegister = { navController.navigate("register") },
                onLoginSuccess = { navController.navigate("home") { popUpTo("login") { inclusive = true } } }
            )
        }
        composable("register") {
            RegisterScreen(
                onNavigateToLogin = { navController.navigate("login") { popUpTo("register") { inclusive = true } } },
                onRegisterSuccess = { navController.navigate("home") { popUpTo("register") { inclusive = true } } }
            )
        }
        composable("home") {
            UserListScreen(
                onUserClick = { user -> navController.navigate("chat/${user.id}") },
                onLogout = { navController.navigate("login") { popUpTo("home") { inclusive = true } } }
            )
        }
        composable("chat/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
            if (userId != null) {
                ChatScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCall = { navController.navigate("call") }
                )
            }
        }
        composable("call") {
            VideoCallScreen(
                onEndCall = { navController.popBackStack() }
            )
        }
    }
}
