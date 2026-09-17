package com.example.offlinegpt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.offlinegpt.ui.mbti.MbtiScreen
import com.example.offlinegpt.ui.mbti.MbtiViewModel
import com.example.offlinegpt.ui.theme.OfflineGPTTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MbtiScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private fun setMbtiContent(viewModel: MbtiViewModel) {
        composeTestRule.setContent {
            OfflineGPTTheme {
                MbtiScreen(
                    viewModel = viewModel,
                    onFinished = {},
                    onBack = {}
                )
            }
        }
    }

    @Test
    fun mbtiScreen_displaysFirstQuestionAndAnswers() {
        hiltRule.inject()
        val viewModel = ViewModelProvider(composeTestRule.activity)[MbtiViewModel::class.java]

        setMbtiContent(viewModel)

        composeTestRule.onNodeWithText("MBTI 진단").assertIsDisplayed()
        composeTestRule.onNodeWithText("나는 새로운 사람들과 만나는 것이 즐겁다.").assertIsDisplayed()

        listOf("매우 그렇다", "그렇다", "보통이다", "아니다", "매우 아니다").forEach { answerText ->
            composeTestRule.onNodeWithText(answerText).assertIsDisplayed()
        }
    }

    @Test
    fun mbtiScreen_selectingAnswer_movesToNextQuestion() {
        hiltRule.inject()
        val viewModel = ViewModelProvider(composeTestRule.activity)[MbtiViewModel::class.java]

        setMbtiContent(viewModel)

        composeTestRule.onNodeWithText("매우 그렇다").performClick()

        composeTestRule.onNodeWithText("나는 혼자 있는 시간보다 사람들과 함께 있을 때 에너지를 얻는다.")
            .assertIsDisplayed()
    }

}
