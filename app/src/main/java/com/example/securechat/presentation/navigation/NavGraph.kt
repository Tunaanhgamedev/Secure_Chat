package com.example.securechat.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.securechat.presentation.chat.ChatScreen
import com.example.securechat.presentation.chat.ChatViewModel
import com.example.securechat.presentation.chat.VideoCallScreen
import com.example.securechat.presentation.groupchat.GroupChatScreen
import com.example.securechat.presentation.home.HomeScreen
import com.example.securechat.presentation.login.LoginScreen
import com.example.securechat.presentation.profile.ProfileScreen
import com.example.securechat.presentation.register.RegisterScreen
import com.google.firebase.auth.FirebaseAuth

@Composable
fun SecureChatNavGraph() {
    val navController = rememberNavController()
    val auth          = FirebaseAuth.getInstance()
    val startDest     = remember { if (auth.currentUser != null) "home" else "login" }

    NavHost(navController = navController, startDestination = startDest) {

        composable("login") {
            LoginScreen(
                onNavigateToRegister = { navController.navigate("register") },
                onLoginSuccess = {
                    navController.navigate("home") { popUpTo("login") { inclusive = true } }
                }
            )
        }

        composable("register") {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.navigate("login") { popUpTo("register") { inclusive = true } }
                },
                onRegisterSuccess = {
                    navController.navigate("home") { popUpTo("register") { inclusive = true } }
                }
            )
        }

        composable("home") {
            HomeScreen(
                onConversationClick = { peerId, peerName ->
                    navController.navigate("chat/$peerId?peerName=$peerName")
                },
                onUserClick = { user ->
                    navController.navigate(
                        "chat/${user.id}?peerName=${user.username.ifBlank { user.email }}"
                    )
                },
                onGroupChatClick = { navController.navigate("group_chat") },
                onProfileClick = { navController.navigate("profile") },
                onLogout = {
                    navController.navigate("login") { popUpTo("home") { inclusive = true } }
                }
            )
        }

        composable("profile") {
            ProfileScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = "chat/{userId}?peerName={peerName}",
            arguments = listOf(
                navArgument("userId")   { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val viewModel: ChatViewModel = hiltViewModel(backStackEntry)
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val peerName = backStackEntry.arguments?.getString("peerName") ?: ""

            ChatScreen(
                viewModel        = viewModel,
                peerName         = peerName,
                onNavigateBack   = { navController.popBackStack() },
                onNavigateToCall = { 
                    navController.navigate("call/$userId?peerName=$peerName&isIncoming=false") 
                }
            )
        }

        composable(
            route = "call/{userId}?peerName={peerName}&isIncoming={isIncoming}",
            arguments = listOf(
                navArgument("userId")     { type = NavType.StringType },
                navArgument("peerName")   { type = NavType.StringType; defaultValue = "" },
                navArgument("isIncoming") { type = NavType.BoolType; defaultValue = false }
            )
        ) {
            VideoCallScreen(
                onEndCall = { navController.popBackStack() }
            )
        }

        composable("group_chat") {
            GroupChatScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
