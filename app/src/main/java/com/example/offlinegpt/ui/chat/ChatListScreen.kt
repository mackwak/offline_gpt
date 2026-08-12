package com.example.offlinegpt.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Chats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
