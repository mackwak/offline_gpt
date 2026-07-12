package com.example.offlinegpt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.offlinegpt.ui.auth.AuthViewModel
import com.example.offlinegpt.ui.auth.LoginScreen
import com.example.offlinegpt.ui.auth.SignupScreen
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
                val viewModel: AuthViewModel = hiltViewModel()
                val startDestination = if (viewModel.isLoggedIn) "home" else "login"

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("login") {
                            val viewModel: AuthViewModel = hiltViewModel()
                            LoginScreen(
                                viewModel = viewModel,
                                onNavigateToSignup = { navController.navigate("signup") },
                                onLoginSuccess = { navController.navigate("home") }
                            )
                        }
                        composable("signup") {
                            val viewModel: AuthViewModel = hiltViewModel()
                            SignupScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onSignupSuccess = { navController.navigate("home") }
                            )
                        }
                        composable("home") {
                            val viewModel: AuthViewModel = hiltViewModel()
                            LaunchedEffect(Unit) {
                                viewModel.events.collect { event ->
                                    if (event is AuthViewModel.AuthEvent.Logout) {
                                        navController.navigate("login") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                }
                            }
                            HomeScreen(onLogout = { viewModel.onLogoutClick() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "Welcome to OfflineGPT!")
        Button(onClick = onLogout) {
            Text("Logout")
        }
    }
}
