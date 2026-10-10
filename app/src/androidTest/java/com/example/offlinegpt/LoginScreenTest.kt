package com.example.offlinegpt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.offlinegpt.ui.auth.LoginScreen
import com.example.offlinegpt.ui.theme.OfflineGPTTheme
import com.example.offlinegpt.ui.auth.AuthViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var viewModel: AuthViewModel

    @Test
    fun loginScreen_inputEmailAndPassword_updatesViewModel() {
        hiltRule.inject()
        viewModel = ViewModelProvider(composeTestRule.activity).get(AuthViewModel::class.java)

        composeTestRule.setContent {
            OfflineGPTTheme {
                LoginScreen(
                    viewModel = viewModel,
                    onNavigateToSignup = {},
                    onLoginSuccess = {}
                )
            }
        }

        val testEmail = "test@example.com"
        val testPassword = "password123"

        // Type email
        composeTestRule.onNodeWithText("Email").performTextInput(testEmail)
        
        // Type password
        composeTestRule.onNodeWithText("Password").performTextInput(testPassword)

        // Verify ViewModel state (optional, but good for testing logic)
        assert(viewModel.email == testEmail)
        assert(viewModel.password == testPassword)
    }
}
