package com.example.offlinegpt.ui.auth

import android.app.Activity
import android.accounts.AccountManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val remoteConfig: FirebaseRemoteConfig,
    private val functions: FirebaseFunctions
) : ViewModel() {

    private companion object {
        const val TAG = "AuthViewModel"
        const val PUBLIC_KEY_CREDENTIAL_TYPE = "androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL"
    }

    private val noCredentialKeywords = listOf("no credential", "no credentials", "no passkey", "no_credential")
    private val canceledKeywords = listOf("canceled", "cancelled", "aborted")
    private val rpMismatchKeywords = listOf("rpid", "relying party", "origin", "securityerror", "not allowed")

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

    fun onPasskeyLoginClick(activity: Activity) {
        viewModelScope.launch {
            val normalizedEmail = normalizeEmail(email)

            if (!isPasskeySupported()) {
                _events.emit(AuthEvent.Error("Passkey is supported on Android 9 (API 28) or higher."))
                return@launch
            }

            if (!isActivityUsable(activity)) {
                _events.emit(AuthEvent.Error("Screen is not active. Please reopen and try passkey sign-in again."))
                return@launch
            }

            if (normalizedEmail.isNullOrBlank()) {
                _events.emit(AuthEvent.Error("Please enter your email to sign in with Passkey"))
                return@launch
            }

            isLoading = true
            try {
                logCredentialContext(activity, normalizedEmail, "login")
                val credential = try {
                    val optionsJson = requestAuthOptionsJson(normalizedEmail)
                    getPasskeyCredential(activity, optionsJson)
                } catch (e: GetCredentialException) {
                    Log.w(TAG, "Scoped passkey getCredential failed (${e.type}: ${e.message}), retrying with discoverable credentials", e)
                    val fallbackOptionsJson = requestAuthOptionsJson(null)
                    getPasskeyCredential(activity, fallbackOptionsJson)
                } catch (e: Exception) {
                    Log.w(TAG, "Scoped passkey failed (${e.message}), retrying with discoverable credentials", e)
                    val fallbackOptionsJson = requestAuthOptionsJson(null)
                    getPasskeyCredential(activity, fallbackOptionsJson)
                }

                if (credential is CustomCredential && credential.type == PUBLIC_KEY_CREDENTIAL_TYPE) {
                    val responseJson = credential.data.getString("androidx.credentials.BUNDLE_KEY_SUBTYPE_GET_PUBLIC_KEY_CREDENTIAL_RESPONSE_JSON")
                        ?: throw Exception("Passkey response missing")

                    completePasskeyLogin(normalizedEmail, responseJson)
                    _events.emit(AuthEvent.Success)
                } else {
                    _events.emit(AuthEvent.Error("Invalid credential type"))
                }
            } catch (e: GetCredentialException) {
                Log.w(TAG, "GetCredentialException type=${e.type}, message=${e.message}", e)
                _events.emit(AuthEvent.Error(mapPasskeyLoginError(e, normalizedEmail)))
            } catch (e: Exception) {
                Log.e(TAG, "Passkey login exception: ${e.javaClass.name}, message=${e.message}", e)
                _events.emit(AuthEvent.Error(mapPasskeyLoginError(e, normalizedEmail)))
            } catch (t: Throwable) {
                Log.e(TAG, "Passkey login throwable: ${t.javaClass.name}, message=${t.message}", t)
                _events.emit(AuthEvent.Error(t.localizedMessage ?: "Passkey authentication failed"))
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun requestAuthOptionsJson(email: String?): String {
        val payload = email?.let { mapOf("email" to it) } ?: emptyMap<String, String>()
        val requestResult = functions
            .getHttpsCallable("generateAuthOptions")
            .call(payload)
            .await()

        val optionsMap = requestResult.data as? Map<*, *>
        val rawJson = if (optionsMap != null) mapToJsonString(optionsMap) else requestResult.data.toString()
        val sanitizedJson = sanitizeAuthOptionsJson(rawJson)
        logAuthOptionsSummary(sanitizedJson, email)
        return sanitizedJson
    }

    private fun sanitizeAuthOptionsJson(optionsJson: String): String {
        return runCatching {
            val json = JSONObject(optionsJson)
            if (json.has("rpID") && !json.has("rpId")) {
                json.put("rpId", json.getString("rpID"))
            } else if (!json.has("rpId") && !json.has("rpID")) {
                json.put("rpId", "offlinegpt-dev.web.app")
            }
            json.toString()
        }.getOrDefault(optionsJson)
    }

    private suspend fun getPasskeyCredential(activity: Activity, optionsJson: String) = withContext(Dispatchers.Main.immediate) {
        val credentialManager = CredentialManager.create(activity)
        val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(optionsJson)
        val getCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(getPublicKeyCredentialOption)
            .setPreferImmediatelyAvailableCredentials(false)
            .build()

        if (!isActivityUsable(activity)) {
            throw IllegalStateException("Screen changed while preparing passkey sign-in. Please try again.")
        }

        try {
            Log.i(TAG, "Calling getCredential. optionsLength=${optionsJson.length}")
            credentialManager.getCredential(activity, getCredentialRequest).credential
        } catch (t: Throwable) {
            Log.e(TAG, "getCredential failed: ${t.javaClass.name}, message=${t.message}", t)
            throw t
        }
    }

    private suspend fun completePasskeyLogin(email: String, responseJson: String) {
        val verifyResult = functions
            .getHttpsCallable("verifyAuth")
            .call(mapOf("email" to email, "credential" to responseJson))
            .await()

        val dataMap = verifyResult.data as? Map<*, *>
        val customToken = (dataMap?.get("customToken") ?: dataMap?.get("token")) as? String
            ?: throw Exception("Custom token not received from server")

        auth.signInWithCustomToken(customToken).await()
    }

    private fun mapPasskeyLoginError(error: Throwable, userEmail: String): String {
        if (error is androidx.credentials.exceptions.NoCredentialException) {
            return "No passkey found for $userEmail on this device/account. Use the same account and register a passkey first."
        }

        if (error is androidx.credentials.exceptions.GetCredentialCancellationException) {
            return "Passkey sign-in was canceled."
        }

        if (error is androidx.credentials.exceptions.GetCredentialUnsupportedException) {
            return "This device does not support passkey sign-in."
        }

        if (error is androidx.credentials.exceptions.GetCredentialProviderConfigurationException) {
            return "Credential provider is not configured on this device. Check Google Play Services/account settings."
        }

        if (error is FirebaseFunctionsException) {
            return when (error.code) {
                FirebaseFunctionsException.Code.NOT_FOUND ->
                    "No registered passkey was found on the server for $userEmail. Please register a passkey first."
                FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                    "The passkey challenge expired or is missing. Please try passkey sign-in again."
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    "Passkey verification failed. Please try again."
                else -> error.localizedMessage ?: "Passkey authentication failed"
            }
        }

        val payload = buildString {
            append(error.localizedMessage.orEmpty())
            if (error is GetCredentialException) {
                append(' ')
                append(error.type)
            }
        }.lowercase()

        return when {
            noCredentialKeywords.any(payload::contains) ->
                "No passkey found for $userEmail on this device/account. Use the same account and register a passkey first."
            canceledKeywords.any(payload::contains) ->
                "Passkey sign-in was canceled."
            rpMismatchKeywords.any(payload::contains) ->
                "This passkey does not match this app environment. Check RP ID/build flavor and try again."
            else -> error.localizedMessage ?: "Passkey authentication failed"
        }
    }

    fun onPasskeyRegisterClick(activity: Activity) {
        viewModelScope.launch {
            val normalizedEmail = normalizeEmail(email)

            if (!isPasskeySupported()) {
                _events.emit(AuthEvent.Error("Passkey is supported on Android 9 (API 28) or higher."))
                return@launch
            }

            if (!isActivityUsable(activity)) {
                _events.emit(AuthEvent.Error("Screen is not active. Please reopen and try passkey registration again."))
                return@launch
            }

            if (normalizedEmail.isNullOrBlank()) {
                _events.emit(AuthEvent.Error("Please enter your email to register Passkey"))
                return@launch
            }
            isLoading = true
            try {
                logCredentialContext(activity, normalizedEmail, "register")
                Log.i(TAG, "Starting passkey registration for email: $normalizedEmail")

                val requestResult = functions
                    .getHttpsCallable("generateRegisterOptions")
                    .call(mapOf("email" to normalizedEmail))
                    .await()

                val optionsMap = requestResult.data as? Map<*, *>
                val optionsJson = if (optionsMap != null) mapToJsonString(optionsMap) else requestResult.data.toString()
                val challenge = extractChallenge(optionsMap, optionsJson)

                val credentialManager = CredentialManager.create(activity)
                val createRequest = CreatePublicKeyCredentialRequest(optionsJson)

                if (!isActivityUsable(activity)) {
                    _events.emit(AuthEvent.Error("Screen changed while preparing passkey registration. Please try again."))
                    return@launch
                }

                val createResponse = try {
                    credentialManager.createCredential(activity, createRequest)
                } catch (t: Throwable) {
                    Log.e(TAG, "createCredential failed: ${t.javaClass.name}, message=${t.message}", t)
                    throw t
                }
                if (createResponse is CreatePublicKeyCredentialResponse) {
                    val registrationResponseJson = createResponse.registrationResponseJson
                    Log.i(
                        TAG,
                        "Passkey createCredential succeeded for $normalizedEmail. registrationJsonLength=${registrationResponseJson.length}, challengeProvided=${!challenge.isNullOrBlank()}"
                    )

                    val dataMap = completePasskeyRegistration(normalizedEmail, registrationResponseJson, challenge)
                    Log.i(
                        TAG,
                        "verifyRegister completed for $normalizedEmail. verified=${dataMap?.get("verified")}, hasCustomToken=${dataMap?.containsKey("customToken") == true}"
                    )

                    val registerToken = (dataMap?.get("customToken") ?: dataMap?.get("token")) as? String
                    if (!registerToken.isNullOrBlank()) {
                        auth.signInWithCustomToken(registerToken).await()
                        _events.emit(AuthEvent.Success)
                        return@launch
                    }

                    _events.emit(AuthEvent.Message("Passkey registered successfully! You can now log in with Passkey."))
                } else {
                    _events.emit(AuthEvent.Error("Passkey registration failed"))
                }
            } catch (e: CreateCredentialException) {
                Log.w(TAG, "CreateCredentialException type=${e.type}, message=${e.message}", e)
                if (e.type.contains("CANCELED", ignoreCase = true) || e.message?.contains("canceled", ignoreCase = true) == true) {
                    _events.emit(AuthEvent.Error("Passkey registration canceled"))
                } else {
                    _events.emit(AuthEvent.Error(mapPasskeyRegisterError(e, normalizedEmail)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Passkey register exception: ${e.javaClass.name}, message=${e.message}", e)
                _events.emit(AuthEvent.Error(mapPasskeyRegisterError(e, normalizedEmail)))
            } catch (t: Throwable) {
                Log.e(TAG, "Passkey register throwable: ${t.javaClass.name}, message=${t.message}", t)
                _events.emit(AuthEvent.Error(mapPasskeyRegisterError(t, normalizedEmail)))
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun completePasskeyRegistration(email: String, registrationResponseJson: String, challenge: String?): Map<*, *>? {
        val result = functions
            .getHttpsCallable("verifyRegister")
            .call(
                buildMap {
                    put("email", email)
                    put("credential", registrationResponseJson)
                    if (!challenge.isNullOrBlank()) put("challenge", challenge)
                }
            )
            .await()

        return result.data as? Map<*, *>
    }

    private fun extractChallenge(optionsMap: Map<*, *>?, optionsJson: String): String? {
        val mapChallenge = optionsMap?.get("challenge") as? String
        if (!mapChallenge.isNullOrBlank()) return mapChallenge

        return runCatching { JSONObject(optionsJson).optString("challenge") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun logAuthOptionsSummary(optionsJson: String, email: String?) {
        runCatching {
            val json = JSONObject(optionsJson)
            val rpId = json.optString("rpId")
            val challenge = json.optString("challenge")
            val allowCredentials = json.optJSONArray("allowCredentials") ?: JSONArray()
            val ids = (0 until allowCredentials.length()).mapNotNull { idx ->
                allowCredentials.optJSONObject(idx)?.optString("id")
            }

            Log.i(
                TAG,
                "Auth options summary email=$email rpId=$rpId challengeLen=${challenge.length} allowCredentialsCount=${allowCredentials.length()} allowCredentialIds=$ids"
            )
        }.onFailure {
            Log.w(TAG, "Failed to parse auth options JSON for logging: ${it.message}")
        }
    }

    private fun mapToJsonString(map: Map<*, *>): String {
        return (toJson(map) as JSONObject).toString()
    }

    private fun toJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> {
            val json = JSONObject()
            value.forEach { (k, v) ->
                if (k != null) json.put(k.toString(), toJson(v))
            }
            json
        }
        is Iterable<*> -> {
            val array = JSONArray()
            value.forEach { array.put(toJson(it)) }
            array
        }
        is Array<*> -> {
            val array = JSONArray()
            value.forEach { array.put(toJson(it)) }
            array
        }
        is Boolean, is Number, is String, is JSONObject, is JSONArray -> value
        else -> value.toString()
    }

    private fun mapPasskeyRegisterError(error: Throwable, userEmail: String): String {
        if (error is FirebaseFunctionsException) {
            return when (error.code) {
                FirebaseFunctionsException.Code.NOT_FOUND ->
                    "Passkey registration session was not found for $userEmail. Start registration again and make sure the latest server code is deployed."
                FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                    "Passkey registration challenge expired or is missing. Please try registering again."
                FirebaseFunctionsException.Code.UNIMPLEMENTED ->
                    "Passkey registration function is not available on the server. Deploy the latest Firebase Functions and try again."
                else -> error.localizedMessage ?: "Passkey registration failed"
            }
        }

        if (error is CreateCredentialException) {
            val payload = buildString {
                append(error.localizedMessage.orEmpty())
                append(' ')
                append(error.type)
            }.lowercase()

            return when {
                canceledKeywords.any(payload::contains) -> "Passkey registration canceled"
                rpMismatchKeywords.any(payload::contains) ->
                    "This passkey does not match this app environment. Check RP ID/build flavor and try again."
                else -> error.localizedMessage ?: "Passkey registration failed"
            }
        }

        return error.localizedMessage ?: "Passkey registration failed"
    }

    private fun logCredentialContext(activity: Activity, normalizedEmail: String, flow: String) {
        runCatching {
            val accounts = AccountManager.get(activity).getAccountsByType("com.google").map { it.name }
            val firebaseUserEmail = auth.currentUser?.email
            Log.i(
                TAG,
                "Credential context flow=$flow package=${activity.packageName} uid=${android.os.Process.myUid()} inputEmail=$normalizedEmail firebaseUser=$firebaseUserEmail googleAccounts=$accounts"
            )
        }.onFailure {
            Log.w(TAG, "Failed to log credential context: ${it.message}")
        }
    }

    private fun normalizeEmail(value: String?): String? {
        if (value == null) return null
        val normalized = value.trim().lowercase()
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun isPasskeySupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    private fun isActivityUsable(activity: Activity): Boolean = !activity.isFinishing && !activity.isDestroyed

    sealed class AuthEvent {
        object Success : AuthEvent()
        object Logout : AuthEvent()
        data class Message(val message: String) : AuthEvent()
        data class Error(val message: String) : AuthEvent()
    }
}
