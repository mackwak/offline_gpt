package com.example.offlinegpt.ui.mbti

import com.example.offlinegpt.data.mbti.MBTIQuestion
import com.example.offlinegpt.data.mbti.MbtiTrait
import com.example.offlinegpt.data.repository.MbtiRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun `answering questions updates index and progress`() = runTest {
        viewModel.onAnswerSelected(2) // Answer 1st
        
        // Use yield() or just direct check with UnconfinedTestDispatcher
        assertEquals(1, viewModel.currentQuestionIndex.value)
        assertEquals(mockQuestions[1], viewModel.currentQuestion.value)
        
        // Progress for 1st answered (index 1) out of 4 is 0.25f
        // Wait, index 1/4 = 0.25f. 
        // Let's print the actual value if it fails.
        assertEquals(0.25f, viewModel.progress.value)
    }

    @Test
    fun `completing all questions calculates ESTJ correctly`() = runTest {
        viewModel.onAnswerSelected(2) // E
        viewModel.onAnswerSelected(2) // S
        viewModel.onAnswerSelected(2) // T
        viewModel.onAnswerSelected(2) // J
        
        val uiState = viewModel.uiState.value
        if (uiState !is MbtiUiState.Finished) {
            fail("Expected Finished state, but was $uiState")
        }
        val result = (uiState as MbtiUiState.Finished).result
        assertEquals("ESTJ", result.mbtiType)
        
        verify(repository).saveResult(any())
    }

    @Test
    fun `completing all questions calculates INFP correctly`() = runTest {
        viewModel.onAnswerSelected(-2) // I
        viewModel.onAnswerSelected(-2) // N
        viewModel.onAnswerSelected(-2) // F
        viewModel.onAnswerSelected(-2) // P
        
        val uiState = viewModel.uiState.value
        if (uiState !is MbtiUiState.Finished) {
            fail("Expected Finished state, but was $uiState")
        }
        val result = (uiState as MbtiUiState.Finished).result
        assertEquals("INFP", result.mbtiType)
    }

    @Test
    fun `restart resets the state`() = runTest {
        viewModel.onAnswerSelected(2)
        viewModel.restart()
        
        assertEquals(0, viewModel.currentQuestionIndex.value)
        assertEquals(MbtiUiState.Assessment, viewModel.uiState.value)
    }
}
