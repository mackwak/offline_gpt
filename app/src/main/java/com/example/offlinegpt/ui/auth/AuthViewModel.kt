package com.example.offlinegpt.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val remoteConfig: FirebaseRemoteConfig,
    private val functions: FirebaseFunctions
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

    fun onPasskeyLoginClick(context: Context) {
        viewModelScope.launch {
            isLoading = true
            try {
                val requestResult = functions
                    .getHttpsCallable("requestRegistration")
                    .call(mapOf("email" to "dpk94icu@gmail.com"))
                    .await()

                val dataMap = requestResult.data as? Map<*, *>
                val message = dataMap?.get("message") as? String
                    ?: dataMap?.get("response") as? String
                    ?: requestResult.data.toString()

                _events.emit(AuthEvent.Error(message))
            } catch (e: Exception) {
                _events.emit(AuthEvent.Error(e.localizedMessage ?: "requestRegistration failed"))
            } finally {
                isLoading = false
            }
        }
    }

    fun onPasskeyRegisterClick(context: Context) {
        viewModelScope.launch {
            if (email.isBlank()) {
                _events.emit(AuthEvent.Error("Please enter your email to register Passkey"))
                return@launch
            }
            isLoading = true
            try {
                val requestResult = functions
                    .getHttpsCallable("requestRegistration")
                    .call(mapOf("email" to email))
                    .await()

                val optionsMap = requestResult.data as? Map<*, *>
                val optionsJson = if (optionsMap != null) JSONObject(optionsMap).toString() else requestResult.data.toString()

                val credentialManager = CredentialManager.create(context)
                val createRequest = CreatePublicKeyCredentialRequest(optionsJson)

                val createResponse = credentialManager.createCredential(context, createRequest)
                if (createResponse is CreatePublicKeyCredentialResponse) {
                    val registrationResponseJson = createResponse.registrationResponseJson

                    functions
                        .getHttpsCallable("verifyRegistration")
                        .call(mapOf("email" to email, "registrationResponse" to registrationResponseJson))
                        .await()

                    _events.emit(AuthEvent.Error("Passkey registered successfully! You can now sign in with Passkey."))
                } else {
                    _events.emit(AuthEvent.Error("Passkey creation failed"))
                }
            } catch (e: Exception) {
                _events.emit(AuthEvent.Error(e.localizedMessage ?: "Passkey registration failed"))
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
