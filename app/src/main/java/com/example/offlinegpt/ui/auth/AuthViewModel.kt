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
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
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
            if (email.isBlank()) {
                _events.emit(AuthEvent.Error("Please enter your email to sign in with Passkey"))
                return@launch
            }
            isLoading = true
            try {
                val requestResult = functions
                    .getHttpsCallable("requestAuthentication")
                    .call(mapOf("email" to email))
                    .await()


                val optionsMap = requestResult.data as? Map<*, *>
                val optionsJson = if (optionsMap != null) JSONObject(optionsMap).toString() else requestResult.data.toString()

                val credentialManager = CredentialManager.create(context)
                val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(optionsJson)
                val getCredentialRequest = GetCredentialRequest(listOf(getPublicKeyCredentialOption))

                val result = credentialManager.getCredential(context, getCredentialRequest)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL) {
                    val responseJson =
                        credential.data.getString("androidx.credentials.BUNDLE_KEY_SUBTYPE_GET_PUBLIC_KEY_CREDENTIAL_RESPONSE_JSON")
                            ?: throw Exception("Passkey response missing")

                    val verifyResult = functions
                        .getHttpsCallable("verifyAuthentication")
                        .call(mapOf("email" to email, "authResponse" to responseJson))
                        .await()

                    val dataMap = verifyResult.data as? Map<*, *>
                    val customToken = dataMap?.get("token") as? String
                        ?: throw Exception("Custom token not received")
                    auth.signInWithCustomToken(customToken).await()
                    _events.emit(AuthEvent.Success)
                } else {
                    throw Exception("Unsupported passkey credential returned")
                }
            } catch (e: Exception) {
                _events.emit(AuthEvent.Error(getPasskeyErrorMessage(e, isLogin = true)))
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
                _events.emit(AuthEvent.Error(getPasskeyErrorMessage(e, isLogin = false)))
            } finally {
                isLoading = false
            }
        }
    }

    private fun getPasskeyErrorMessage(exception: Exception, isLogin: Boolean): String {
        return when (exception) {
            is NoCredentialException -> "No passkey available for this account on this device. Register a passkey first, or choose a device/account where one already exists."
            is FirebaseFunctionsException -> {
                val detailText = when (val details = exception.details) {
                    is Map<*, *> -> details["rawMessage"] as? String
                    else -> details?.toString()
                }
                buildString {
                    append("${exception.code.name}: ")
                    append(exception.message ?: if (isLogin) "Passkey sign-in failed" else "Passkey registration failed")
                    if (!detailText.isNullOrBlank() && detailText != exception.message) {
                        append("\n")
                        append(detailText)
                    }
                }
            }
            is GetCredentialException -> exception.localizedMessage
                ?: if (isLogin) "Passkey sign-in failed" else "Could not access passkeys on this device"
            is CreateCredentialException -> exception.localizedMessage ?: "Passkey registration failed"
            else -> exception.localizedMessage
                ?: if (isLogin) "Passkey sign-in failed" else "Passkey registration failed"
        }
    }

    sealed class AuthEvent {
        object Success : AuthEvent()
        object Logout : AuthEvent()
        data class Error(val message: String) : AuthEvent()
    }
}
