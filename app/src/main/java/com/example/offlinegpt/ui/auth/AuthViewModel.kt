package com.example.offlinegpt.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val remoteConfig: FirebaseRemoteConfig,
    private val functions: FirebaseFunctions,
    private val credentialManager: CredentialManager
) : ViewModel() {

    companion object {
        private const val GMS_BROKER_PACKAGE_ERROR = "Unknown calling package name 'com.google.android.gms'"
    }

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

    fun fetchHelloWorldOnLaunch() {
        viewModelScope.launch {
            try {
                val result = functions.getHttpsCallable("helloWorldOnCall").call().await()
                val payload = result.data as? Map<*, *>
                val response = payload?.get("response") as? String
                    ?: payload?.get("message") as? String
                    ?: result.data?.toString().orEmpty()

                Log.d("AuthViewModel", "helloWorldOnCall response: $response")
                _events.emit(AuthEvent.Error("Callable response: $response"))
            } catch (e: Exception) {
                val message = e.localizedMessage ?: "Failed to call helloWorldOnCall"
                Log.e("AuthViewModel", message, e)
                _events.emit(AuthEvent.Error(message))
            }
        }
    }

    fun onPasskeyLoginClick(context: Context) {
        viewModelScope.launch {
            if (email.isBlank()) {
                _events.emit(AuthEvent.Error("Email is required for passkey login"))
                return@launch
            }
            if (!ensureGooglePlayServicesReady(context)) return@launch

            isLoading = true
            try {
                val challengeResult = functions.getHttpsCallable("requestAuthentication")
                    .call(mapOf("email" to email))
                    .await()

                val authOptionsJson = toJsonString(challengeResult.data)
                val option = GetPublicKeyCredentialOption(requestJson = authOptionsJson)
                val getCredentialRequest = GetCredentialRequest(listOf(option))
                val credentialResult = credentialManager.getCredential(context, getCredentialRequest)
                val credential = credentialResult.credential as? PublicKeyCredential
                    ?: throw IllegalStateException("Unsupported credential type")

                val verifyResult = functions.getHttpsCallable("verifyAuthentication")
                    .call(
                        mapOf(
                            "email" to email,
                            "authResponse" to credential.authenticationResponseJson
                        )
                    )
                    .await()

                val customToken = (verifyResult.data as? Map<*, *>)?.get("token") as? String
                    ?: throw IllegalStateException("Missing custom token")
                auth.signInWithCustomToken(customToken).await()
                _events.emit(AuthEvent.Success)
            } catch (e: GetCredentialException) {
                _events.emit(AuthEvent.Error("Passkey failed: ${friendlyPasskeyError(e)}"))
            } catch (e: FirebaseFunctionsException) {
                Log.e(
                    "AuthViewModel",
                    "requestAuthentication failed code=${e.code} message=${e.message} details=${e.details}",
                    e
                )
                _events.emit(AuthEvent.Error(functionsErrorMessage(e)))
            } catch (e: SecurityException) {
                _events.emit(AuthEvent.Error("Passkey failed: ${friendlyPasskeyError(e)}"))
            } catch (e: Exception) {
                _events.emit(AuthEvent.Error(e.localizedMessage ?: "Passkey login failed"))
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun ensureGooglePlayServicesReady(context: Context): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val status = availability.isGooglePlayServicesAvailable(context)
        if (status == ConnectionResult.SUCCESS) return true

        val message = if (availability.isUserResolvableError(status)) {
            "Google Play services 업데이트 또는 로그인 후 다시 시도해 주세요."
        } else {
            "이 기기에서는 Google Play services를 사용할 수 없어 Passkey를 진행할 수 없습니다."
        }
        _events.emit(AuthEvent.Error(message))
        return false
    }

    private fun toJsonString(data: Any?): String {
        return when (data) {
            is String -> data
            is Map<*, *> -> toJsonObject(data).toString()
            else -> JSONObject.wrap(data)?.toString().orEmpty()
        }
    }

    private fun toJsonObject(map: Map<*, *>): JSONObject {
        val jsonObject = JSONObject()
        for ((key, value) in map) {
            jsonObject.put(key.toString(), toJsonValue(value))
        }
        return jsonObject
    }

    private fun toJsonValue(value: Any?): Any? {
        return when (value) {
            is Map<*, *> -> toJsonObject(value)
            is List<*> -> JSONArray(value.map { toJsonValue(it) })
            else -> JSONObject.wrap(value)
        }
    }

    private fun friendlyPasskeyError(e: Exception): String {
        val detail = e.localizedMessage.orEmpty()
        return if (detail.contains(GMS_BROKER_PACKAGE_ERROR)) {
            "Google Play services 세션 오류입니다. Play services 업데이트/재로그인 후 다시 시도해 주세요."
        } else {
            detail.ifBlank { "알 수 없는 Passkey 오류" }
        }
    }

    private fun functionsErrorMessage(e: FirebaseFunctionsException): String {
        return when (e.code) {
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                "권한이 거부되었습니다. Cloud Functions invoker 권한(invoker: 'public'), App Check 설정, 또는 Firebase 프로젝트/리전(us-central1) 일치 여부를 확인해 주세요."
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "인증 정보가 없습니다. 다시 로그인 후 시도해 주세요."
            FirebaseFunctionsException.Code.NOT_FOUND ->
                "등록된 Passkey가 없거나 계정을 찾을 수 없습니다."
            else -> "Passkey failed (${e.code.name}): ${e.message}"
        }
    }

    sealed class AuthEvent {
        object Success : AuthEvent()
        object Logout : AuthEvent()
        data class Error(val message: String) : AuthEvent()
    }
}
