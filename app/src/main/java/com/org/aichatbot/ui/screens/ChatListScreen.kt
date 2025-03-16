package com.org.aichatbot.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

// Simple chat item data class
data class ChatItem(
    val id: Int,
    val title: String,
    val messageCount: Int,
    val lastUpdated: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatSelected: (Int) -> Unit,
    onNewChat: () -> Unit,
    onBackToHome: () -> Unit
) {
    // Sample data for chat list
    val sampleChats = remember {
        mutableStateListOf(
            ChatItem(1, "General Chat", 4, System.currentTimeMillis() - 3600000),
            ChatItem(2, "Tech Discussion", 7, System.currentTimeMillis() - 86400000),
            ChatItem(3, "Travel Planning", 12, System.currentTimeMillis() - 172800000)
        )
    }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedChat by remember { mutableStateOf<ChatItem?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Chats") },
            navigationIcon = {
                IconButton(onClick = onBackToHome) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(sampleChats) { chat ->
                ChatListItem(
                    chat = chat,
                    onClick = { onChatSelected(chat.id) },
                    onDelete = {
                        selectedChat = chat
                        showDeleteDialog = true
                    },
                    onEdit = {
                        selectedChat = chat
                        newTitle = chat.title
                        showEditDialog = true
                    }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Chat") },
            text = { Text("Are you sure you want to delete this chat?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedChat?.let { chat ->
                            // Remove the chat from the list
                            sampleChats.remove(chat)
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Chat Title") },
            text = {
                TextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedChat?.let { chat ->
                            val index = sampleChats.indexOf(chat)
                            if (index != -1) {
                                sampleChats[index] = chat.copy(title = newTitle)
                            }
                        }
                        showEditDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChatListItem(
    chat: ChatItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Messages: ${chat.messageCount}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Last updated: ${formatTimestamp(chat.lastUpdated)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
