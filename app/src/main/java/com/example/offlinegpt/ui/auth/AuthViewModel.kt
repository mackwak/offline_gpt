package com.example.offlinegpt.ui.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val remoteConfig: FirebaseRemoteConfig,
    private val credentialManager: CredentialManager
) : ViewModel() {

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    val appName: String
        get() = remoteConfig.getString("APP_NAME")

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set

    private val _events = MutableSharedFlow<AuthEvent>()
    val events = _events.asSharedFlow()

    fun onLoginClick() {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                _events.emit(AuthEvent.Error("Email and password cannot be empty"))
                return@launch
            }
            isLoading = true
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _events.emit(AuthEvent.Success)
            } catch (e: Exception) {
                _events.emit(AuthEvent.Error(e.localizedMessage ?: "Login failed"))
            } finally {
                isLoading = false
            }
        }
    }

    fun onSignupClick() {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                _events.emit(AuthEvent.Error("Email and password cannot be empty"))
                return@launch
            }
            isLoading = true
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                _events.emit(AuthEvent.Success)
            } catch (e: Exception) {
                _events.emit(AuthEvent.Error(e.localizedMessage ?: "Signup failed"))
            } finally {
                isLoading = false
            }
        }
    }

    fun onLogoutClick() {
        auth.signOut()
        viewModelScope.launch {
            _events.emit(AuthEvent.Logout)
        }
    }

    fun registerPasskey(context: Context) {
        viewModelScope.launch {
            isLoading = true
            try {
                // 1. Fetch passkey creation options JSON from your backend server
                // Example mock JSON from server:
                val creationOptionsJson = """{"challenge": "mock_challenge_bytes", "rp": {"name": "OfflineGPT App", "id": "offlinegpt.example.com"}, "user": {"id": "user_id_123", "name": "$email", "displayName": "$email"}, "pubKeyCredParams": [{"type": "public-key", "alg": -7}]}"""

                val request = CreatePublicKeyCredentialRequest(requestJson = creationOptionsJson)
                val result = credentialManager.createCredential(context, request)
                
                // 2. Send result.registrationResponseJson to your backend server to verify & complete registration
                // val responseJson = result.registrationResponseJson
                
                _events.emit(AuthEvent.Error("Passkey registered successfully on device! (Complete server verification)"))
            } catch (e: Exception) {
                _events.emit(AuthEvent.Error("Passkey registration failed: ${e.localizedMessage}"))
            } finally {
                isLoading = false
            }
        }
    }

    fun loginWithPasskey(context: Context) {
        viewModelScope.launch {
            isLoading = true
            try {
                // 1. Fetch passkey authentication options JSON (challenge) from your backend server
                // Example mock JSON from server:
                val authOptionsJson = """{"challenge": "mock_auth_challenge_bytes", "rpId": "offlinegpt.example.com", "userVerification": "required"}"""

                val publicKeyOption = GetPublicKeyCredentialOption(requestJson = authOptionsJson)
                val getCredRequest = GetCredentialRequest(listOf(publicKeyOption))
                
                val result = credentialManager.getCredential(context, getCredRequest)
                val credential = result.credential
                
                if (credential is PublicKeyCredential) {
                    // 2. Send credential.authenticationResponseJson to your backend server to verify
                    // 3. Server verifies and returns a Firebase Custom Token
                    // val firebaseCustomToken = backendService.verifyAssertion(credential.authenticationResponseJson)
                    
                    // 4. Authenticate into Firebase Auth with the custom token:
                    // auth.signInWithCustomToken(firebaseCustomToken).await()
                    // _events.emit(AuthEvent.Success)
                    
                    _events.emit(AuthEvent.Error("Passkey authenticated on device! Integrate backend Custom Token next."))
                }
            } catch (e: Exception) {
                _events.emit(AuthEvent.Error("Passkey authentication failed: ${e.localizedMessage}"))
            } finally {
                isLoading = false
            }
        }
    }

    sealed class AuthEvent {
        object Success : AuthEvent()
        object Logout : AuthEvent()
        data class Error(val message: String) : AuthEvent()
    }
}
