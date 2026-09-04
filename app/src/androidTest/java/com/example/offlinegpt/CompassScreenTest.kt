package com.example.offlinegpt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.offlinegpt.ui.compass.CompassScreen
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
class CompassScreenTest {

    @Inject
    lateinit var auth: FirebaseAuth

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Test
    fun compassScreen_displaysInitialNorth() {
        hiltRule.inject()
        composeTestRule.setContent {
            OfflineGPTTheme {
                CompassScreen(onBack = {})
            }
        }
    }
}
