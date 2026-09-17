package com.example.offlinegpt.ui.mbti

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.offlinegpt.data.mbti.MBTIQuestion
import com.example.offlinegpt.data.mbti.MbtiTrait
import com.example.offlinegpt.data.repository.MbtiRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MbtiViewModelTest {

    private lateinit var viewModel: MbtiViewModel
    private val repository: MbtiRepository = mock()
    private val auth: FirebaseAuth = mock()
    private val firebaseUser: FirebaseUser = mock()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val mockQuestions = listOf(
        MBTIQuestion(1, "E question", MbtiTrait.E_I, 1),
        MBTIQuestion(2, "S question", MbtiTrait.S_N, 1),
        MBTIQuestion(3, "T question", MbtiTrait.T_F, 1),
        MBTIQuestion(4, "J question", MbtiTrait.J_P, 1)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        whenever(repository.getQuestions()).thenReturn(mockQuestions)
        whenever(repository.getHistory(any())).thenReturn(flowOf(emptyList()))
        whenever(auth.currentUser).thenReturn(firebaseUser)
        whenever(firebaseUser.email).thenReturn("test@example.com")

        viewModel = MbtiViewModel(repository, auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Assessment`() = runTest {
        assertEquals(MbtiUiState.Assessment, viewModel.uiState.value)
        assertEquals(0, viewModel.currentQuestionIndex.value)
        assertEquals(mockQuestions[0], viewModel.currentQuestion.value)
    }

    @Test
    fun `answering questions updates index`() = runTest {
        viewModel.onAnswerSelected(2) 
        assertEquals(1, viewModel.currentQuestionIndex.value)
    }

    @Test
    fun `restart resets the state`() = runTest {
        viewModel.onAnswerSelected(2)
        viewModel.restart()
        
        assertEquals(0, viewModel.currentQuestionIndex.value)
        assertEquals(MbtiUiState.Assessment, viewModel.uiState.value)
    }
}
