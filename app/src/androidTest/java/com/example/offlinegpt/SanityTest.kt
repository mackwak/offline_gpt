package com.example.offlinegpt

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.offlinegpt.ui.theme.OfflineGPTTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SanityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Test
    fun sanity_check_compose_works() {
        hiltRule.inject()
        
        composeTestRule.setContent {
            OfflineGPTTheme {
                Text("Sanity Check")
            }
        }

        // Wait for the node to appear with an increased timeout (5000ms)
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(hasText("Sanity Check"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule
            .onNodeWithText("Sanity Check")
            .assertIsDisplayed()
    }
}
