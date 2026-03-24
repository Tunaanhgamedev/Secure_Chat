package com.example.securechat.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.securechat.presentation.chat.ChatScreen
import com.example.securechat.presentation.login.LoginScreen
import com.example.securechat.presentation.register.RegisterScreen

@Composable
fun SecureChatNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
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
            ChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
