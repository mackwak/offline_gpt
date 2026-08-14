package com.example.offlinegpt.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import com.example.offlinegpt.data.local.ChatSession
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatViewModel,
    onChatClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val sessions by viewModel.sessions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val searchResults = viewModel.searchResults.collectAsState(null)
    val view = LocalView.current
    DisposableEffect(Unit) {
        onDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Chats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(onClick = {

                    viewModel.clearContext()
                    viewModel.seedContext(
                        arrayOf(
                            "I have more than 15 years of strong R&D work experience across Australia and Korea, achieving proven results in each position.",
                            "I am currently at the most experienced and impactful stage of my career and am looking for a position to dedicate my remaining professional energy.",
                            "Having spent nearly 10 years at my current company, I am ready to pursue a new challenge and adventure.",
                            "I can design, write, and test software across both iOS and Android development environments—a versatile skill set honed in small tech teams.",
                            "I actively use AI pair programming tools like GitHub Copilot, which has increased my efficiency and performance by over 30%.",
                            "I am eager to transition to a new work environment where I can contribute long-term over the next decade.",
                            "I am particularly interested in bringing my mobile development and R&D expertise to the ANZ gaming industry."
                        )
                    )
                    viewModel.queryContexts("who is developer")
                }) {
                    Text("Seed")
                    Icon(Icons.Default.Add, contentDescription = "Add context")
                }

                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Text("Chat")
                    Icon(Icons.Default.Add, contentDescription = "New Chat")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (searchResults.value?.isNotEmpty() == true) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Search Results:",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        searchResults.value?.forEachIndexed { index, string ->
                            Text(
                                text = "${index + 1}. $string",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(sessions) { session ->
                    ChatSessionItem(
                        session = session,
                        onClick = { onChatClick(session.id) },
                        onDelete = { viewModel.deleteSession(session.id) }
                    )
                }
            }

        }


    }




    if (showAddDialog) {
        var newChatTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Chat") },
            text = {
                TextField(
                    value = newChatTitle,
                    onValueChange = { newChatTitle = it },
                    placeholder = { Text("Chat Title") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newChatTitle.isNotBlank()) {
                        viewModel.createNewSession(newChatTitle)
                        showAddDialog = false
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChatSessionItem(
    session: ChatSession,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val date = remember(session.createdAt) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(session.createdAt))
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(session.title) },
        supportingContent = { Text(date) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Chat")
            }
        }
    )
    HorizontalDivider()
}
