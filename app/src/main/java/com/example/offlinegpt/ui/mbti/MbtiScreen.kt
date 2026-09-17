package com.example.offlinegpt.ui.mbti

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinegpt.data.local.MBTIResult
import com.example.offlinegpt.data.mbti.MbtiDescriptions

@Composable
fun MbtiScreen(
    viewModel: MbtiViewModel,
    onFinished: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("MBTI 진단") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is MbtiUiState.Assessment -> {
                    val currentQuestion by viewModel.currentQuestion.collectAsState()
                    val progress by viewModel.progress.collectAsState()
                    
                    AssessmentContent(
                        questionText = currentQuestion?.text ?: "",
                        progress = progress,
                        onAnswer = { viewModel.onAnswerSelected(it) }
                    )
                }
                is MbtiUiState.Finished -> {
                    ResultContent(
                        result = state.result,
                        onRestart = { viewModel.restart() },
                        onDone = onFinished
                    )
                }
            }
        }
    }
}

@Composable
fun AssessmentContent(
    questionText: String,
    progress: Float,
    onAnswer: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = questionText,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        AnswerButtons(onAnswer = onAnswer)
    }
}

@Composable
fun AnswerButtons(onAnswer: (Int) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { onAnswer(2) },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) { Text("매우 그렇다") }
        
        Button(
            onClick = { onAnswer(1) },
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) { Text("그렇다") }
        
        OutlinedButton(
            onClick = { onAnswer(0) },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) { Text("보통이다") }
        
        Button(
            onClick = { onAnswer(-1) },
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) { Text("아니다") }
        
        Button(
            onClick = { onAnswer(-2) },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) { Text("매우 아니다") }
    }
}

@Composable
fun ResultContent(
    result: MBTIResult,
    onRestart: () -> Unit,
    onDone: () -> Unit
) {
    val description = remember(result.mbtiType) {
        MbtiDescriptions.getDescription(result.mbtiType)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "당신의 MBTI 결과는",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = result.mbtiType,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
                lineHeight = 24.sp
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("확인")
        }
        
        TextButton(onClick = onRestart) {
            Text("다시 검사하기")
        }
    }
}
