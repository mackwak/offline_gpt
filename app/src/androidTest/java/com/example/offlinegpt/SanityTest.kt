package com.example.offlinegpt

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SanityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sanity_check_compose_works() {
        composeTestRule.setContent {
            Text("Sanity Check")
        }

        composeTestRule
            .onNodeWithText("Sanity Check")
            .assertIsDisplayed()
    }
}