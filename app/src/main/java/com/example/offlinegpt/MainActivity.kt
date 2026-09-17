package com.example.offlinegpt

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.example.offlinegpt.ui.mbti.MbtiScreen
import com.example.offlinegpt.ui.mbti.MbtiHistoryScreen
import com.example.offlinegpt.ui.mbti.MbtiViewModel
import com.example.offlinegpt.ui.calculator.CalculatorScreen
import com.example.offlinegpt.ui.compass.CompassScreen
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
                val startDestination = if (authViewModel.isLoggedIn) "home" else "home"

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { }

                LaunchedEffect(Unit) {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = "home",
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
                            onNavigateToRag = { navController.navigate("chat_rag") },
                            onNavigateToMbti = { navController.navigate("mbti") },
                            onNavigateToMbtiHistory = { navController.navigate("mbti_history") },
                            onNavigateToCalculator = { navController.navigate("calculator") },
                            onNavigateToCompass = { navController.navigate("compass") }
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
                    composable("chat_rag") {
                        val chatViewModel: ChatViewModel = hiltViewModel()
                        ChatDetailScreen(
                            viewModel = chatViewModel,
                            sessionId = -1,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("mbti") {
                        val mbtiViewModel: MbtiViewModel = hiltViewModel()
                        MbtiScreen(
                            viewModel = mbtiViewModel,
                            onFinished = { navController.popBackStack() },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("mbti_history") {
                        val mbtiViewModel: MbtiViewModel = hiltViewModel()
                        MbtiHistoryScreen(
                            viewModel = mbtiViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("calculator") {
                        CalculatorScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("compass") {
                        val chatViewModel: ChatViewModel = hiltViewModel()
                        CompassScreen(
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
    onNavigateToRag: () -> Unit,
    onNavigateToMbti: () -> Unit,
    onNavigateToMbtiHistory: () -> Unit,
    onNavigateToCalculator: () -> Unit,
    onNavigateToCompass: () -> Unit
) {
    val chatViewModel: ChatViewModel = hiltViewModel()
    val errorMessage = chatViewModel.currentStreamingText.collectAsState(null)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Welcome to OfflineGPT!", style = MaterialTheme.typography.headlineMedium)

        if (chatViewModel.checkIfGemmaModelExist()) {
            Button(onClick = onNavigateToChats, modifier = Modifier.fillMaxWidth()) {
                Text("GPT Chats")
            }
        } else {
            Button(onClick = { chatViewModel.downloadGemma4Model() }, modifier = Modifier.fillMaxWidth()) {
                Text("Download Gemma 4 Model")
            }
        }

        if (chatViewModel.checkIfEmbeddingFileExist()) {
            Button(onClick = onNavigateToRag, modifier = Modifier.fillMaxWidth()) {
                Text("RAG Chats")
            }
        } else {
            Button(onClick = { chatViewModel.downloadModelFile() }, modifier = Modifier.fillMaxWidth()) {
                Text("Download Embedding Model")
            }
        }

        Button(onClick = onNavigateToCalculator, modifier = Modifier.fillMaxWidth()) {
            Text("Calculator")
        }

        Button(onClick = onNavigateToCompass, modifier = Modifier.fillMaxWidth()) {
            Text("Compass")
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNavigateToMbti, modifier = Modifier.weight(1f)) {
                Text("MBTI 진단")
            }
            OutlinedButton(onClick = onNavigateToMbtiHistory, modifier = Modifier.weight(1f)) {
                Text("진단 기록")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Logout")
        }
    }
}
