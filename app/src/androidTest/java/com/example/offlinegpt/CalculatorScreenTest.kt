package com.example.offlinegpt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.offlinegpt.ui.calculator.CalculatorScreen
import com.example.offlinegpt.ui.theme.OfflineGPTTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalculatorScreenTest {

    @Inject
    lateinit var auth: FirebaseAuth

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Test
    fun calculator_addition_works() {
        hiltRule.inject()
        composeTestRule.setContent {
            OfflineGPTTheme {
                CalculatorScreen(onBack = {})
            }
        }

        // Input numbers
        composeTestRule.onNodeWithText("Number 1").performTextInput("10")
        composeTestRule.onNodeWithText("Number 2").performTextInput("5")

        // Click add
        composeTestRule.onNodeWithText("+").performClick()

        // Verify result
        composeTestRule.onNodeWithText("Congratulations! Result: 15.0").assertIsDisplayed()
    }

    @Test
    fun calculator_subtraction_works() {
        hiltRule.inject()
        composeTestRule.setContent {
            OfflineGPTTheme {
                CalculatorScreen(onBack = {})
            }
        }

        // Input numbers
        composeTestRule.onNodeWithText("Number 1").performTextInput("20")
        composeTestRule.onNodeWithText("Number 2").performTextInput("8")

        // Click subtract
        composeTestRule.onNodeWithText("-").performClick()

        // Verify result
        composeTestRule.onNodeWithText("Congratulations! Result: 12.0").assertIsDisplayed()
    }

    @Test
    fun calculator_invalidInput_showsFailed() {
        hiltRule.inject()
        composeTestRule.setContent {
            OfflineGPTTheme {
                CalculatorScreen(onBack = {})
            }
        }

        // Input invalid number
        composeTestRule.onNodeWithText("Number 1").performTextInput("abc")
        composeTestRule.onNodeWithText("Number 2").performTextInput("5")

        // Click add
        composeTestRule.onNodeWithText("+").performClick()

        // Verify failure message
        composeTestRule.onNodeWithText("Failed: Invalid input").assertIsDisplayed()
    }
}
