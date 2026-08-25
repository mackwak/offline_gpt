package com.example.offlinegpt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.offlinegpt.ui.auth.AuthViewModel
import com.example.offlinegpt.ui.auth.LoginScreen
import com.example.offlinegpt.ui.auth.SignupScreen
import com.example.offlinegpt.ui.chat.ChatDetailScreen
import com.example.offlinegpt.ui.chat.ChatListScreen
import com.example.offlinegpt.ui.chat.ChatViewModel
import com.example.offlinegpt.ui.calculator.CalculatorScreen
import com.example.offlinegpt.ui.theme.OfflineGPTTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OfflineGPTTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = hiltViewModel()
                val startDestination = if (authViewModel.isLoggedIn) "home" else "login"

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("login") {
                        LoginScreen(
                            viewModel = authViewModel,
                            onNavigateToSignup = { navController.navigate("signup") },
                            onLoginSuccess = { navController.navigate("home") }
                        )
                    }
                    composable("signup") {
                        SignupScreen(
                            viewModel = authViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onSignupSuccess = { navController.navigate("home") }
                        )
                    }
                    composable("home") {
                        LaunchedEffect(Unit) {
                            authViewModel.events.collect { event ->
                                if (event is AuthViewModel.AuthEvent.Logout) {
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            }
                        }
                        HomeScreen(
                            onLogout = { authViewModel.onLogoutClick() },
                            onNavigateToChats = { navController.navigate("chat_list") },
                            onNavigateToCalculator = { navController.navigate("calculator") }
                        )
                    }
                    composable("chat_list") {
                        val chatViewModel: ChatViewModel = hiltViewModel()
                        ChatListScreen(
                            viewModel = chatViewModel,
                            onChatClick = { sessionId ->
                                navController.navigate("chat_detail/$sessionId")
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("chat_detail/{sessionId}") { backStackEntry ->
                        val chatViewModel: ChatViewModel = hiltViewModel()
                        val sessionId = backStackEntry.arguments?.getString("sessionId")?.toLong() ?: 0L
                        ChatDetailScreen(
                            viewModel = chatViewModel,
                            sessionId = sessionId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("calculator") {
                        CalculatorScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onNavigateToChats: () -> Unit,
    onNavigateToCalculator: () -> Unit
) {
    val chatViewModel: ChatViewModel = hiltViewModel()
    val errorMessage = chatViewModel.currentStreamingText.collectAsState(null)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Text(text = "Welcome to OfflineGPT!")

        if (chatViewModel.checkIfEmbeddingFileExist()) {
            Button(onClick = onNavigateToChats) {
                Text("My Embedding Chats")
            }
        } else {
            Button(onClick = { chatViewModel.downloadModelFile() }) {
                Text("Download Gemma 4 Model")
            }
        }

        Button(onClick = onNavigateToCalculator) {
            Text("Calculator")
        }

        Button(onClick = onLogout) {
            Text("Logout")
        }
    }
}
