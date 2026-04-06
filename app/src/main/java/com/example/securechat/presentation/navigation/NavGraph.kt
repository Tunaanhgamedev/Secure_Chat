package com.example.securechat.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.example.securechat.presentation.call.*
import com.example.securechat.presentation.groupchat.create.CreateGroupScreen
import com.example.securechat.presentation.groupchat.custom.CustomGroupChatScreen
import com.example.securechat.presentation.groupchat.custom.CustomGroupChatViewModel
import com.example.securechat.presentation.groupchat.info.GroupInfoScreen
import com.example.securechat.presentation.groupchat.info.GroupInfoViewModel

@Composable
fun SecureChatNavGraph() {
    val navController = rememberNavController()
    val auth          = FirebaseAuth.getInstance()
    val startDest     = remember { if (auth.currentUser != null) "home" else "login" }

    val callManagerViewModel: CallManagerViewModel = hiltViewModel()
    val incomingCall by callManagerViewModel.incomingCall.collectAsState()

    // Show Dialog if there's an incoming call ringing
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val isNotOnCallScreen = currentRoute == null || !currentRoute.startsWith("call/")

    // Safety check for incomingCall notification
    incomingCall?.let { call ->
        if (call.status == "ringing" && isNotOnCallScreen) {
            IncomingCallDialog(
                callModel = call,
                onAccept = {
                    callManagerViewModel.acceptCall(call.callerId) {
                        val encodedName = Uri.encode(call.callerName)
                        navController.navigate("call/${call.callerId}?peerName=$encodedName&isIncoming=true")
                    }
                },
                onDecline = {
                    callManagerViewModel.declineCall(call.callerId)
                }
            )
        }
    }

    NavHost(navController = navController, startDestination = startDest) {

        composable("login") {
            LoginScreen(
                onNavigateToRegister = { navController.navigate("register") },
                onLoginSuccess = {
                    navController.navigate("home") { 
                        popUpTo("login") { inclusive = true } 
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("register") {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.navigate("login") { popUpTo("register") { inclusive = true } }
                },
                onRegisterSuccess = {
                    navController.navigate("home") { 
                        popUpTo("register") { inclusive = true } 
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                onConversationClick = { peerId, peerName ->
                    val encodedName = Uri.encode(peerName)
                    navController.navigate("chat/$peerId?peerName=$encodedName")
                },
                onCustomGroupClick = { groupId, groupName ->
                    val encodedName = Uri.encode(groupName)
                    navController.navigate("custom_group_chat/$groupId?groupName=$encodedName")
                },
                onUserClick = { user ->
                    val name = user.username.ifBlank { user.email }
                    val encodedName = Uri.encode(name)
                    navController.navigate("chat/${user.id}?peerName=$encodedName")
                },
                onGroupChatClick = { navController.navigate("group_chat") },
                onCreateGroupClick = { navController.navigate("create_group") },
                onProfileClick = { navController.navigate("profile") },
                onLogout = {
                    navController.navigate("login") { 
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("create_group") {
            CreateGroupScreen(
                onNavigateBack = { navController.popBackStack() },
                onGroupCreated = { groupId ->
                    navController.popBackStack()
                    // Optionally navigate straight to the new group
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
                    val encodedName = Uri.encode(peerName)
                    navController.navigate("call/$userId?peerName=$encodedName&isIncoming=false") 
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

        composable(
            route = "custom_group_chat/{groupId}?groupName={groupName}",
            arguments = listOf(
                navArgument("groupId")   { type = NavType.StringType },
                navArgument("groupName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
val viewModel: CustomGroupChatViewModel = hiltViewModel(backStackEntry)
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val groupName = backStackEntry.arguments?.getString("groupName") ?: ""

CustomGroupChatScreen(
                viewModel = viewModel,
                groupNameArg = groupName,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGroupInfo = { gid ->
                    navController.navigate("custom_group_info/$gid")
                }
            )
        }

        composable(
            route = "custom_group_info/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
val viewModel: GroupInfoViewModel = hiltViewModel(backStackEntry)
            GroupInfoScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateHome = { 
                    navController.navigate("home") { popUpTo("home") { inclusive = true } }
                }
            )
        }

        composable("group_chat") {
            GroupChatScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
