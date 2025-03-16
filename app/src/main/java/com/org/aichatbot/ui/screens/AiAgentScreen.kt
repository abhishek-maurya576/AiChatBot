package com.org.aichatbot.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.org.aichatbot.model.ChatMessage
import com.org.aichatbot.viewmodel.ChatViewModel
import com.org.aichatbot.viewmodel.AgentViewModel
import com.org.aichatbot.service.AppControlManager

private const val TAG = "AiAgentScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentScreen(
    chatViewModel: ChatViewModel = viewModel(),
    apiKey: String,
    onBackToHome: () -> Unit,
    isMonitoringEnabled: Boolean = false,
    onToggleMonitoring: (Boolean) -> Unit = {}
) {
    // Create the AgentViewModel with the app context
    val context = LocalContext.current
    val viewModelFactory = remember { AgentViewModel.Factory(context, apiKey) }
    val agentViewModel: AgentViewModel = viewModel(factory = viewModelFactory)

    val messages by agentViewModel.messages.collectAsState()
    val isLoading by agentViewModel.isLoading.collectAsState()
    val currentTypingMessage by agentViewModel.currentTypingMessage.collectAsState()
    val isCommand by agentViewModel.isCommand.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // State for showing the accessibility permission dialog
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    // Check if accessibility service is enabled on screen launch
    LaunchedEffect(Unit) {
        val appControlManager = AppControlManager(context)
        if (!appControlManager.isAccessibilityServiceEnabled()) {
            showAccessibilityDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("AI Agent") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF26A69A),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            ),
            navigationIcon = {
                IconButton(onClick = onBackToHome) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                // Monitoring toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onToggleMonitoring(!isMonitoringEnabled) }
                    )
                ) {
                    Switch(
                        checked = isMonitoringEnabled,
                        onCheckedChange = { onToggleMonitoring(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF1DE9B6)
                        )
                    )
                    Text(
                        text = "Monitor",
                        color = Color.White,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                
                // Accessibility settings button
                IconButton(
                    onClick = { 
                        val appControlManager = AppControlManager(context)
                        appControlManager.openAccessibilitySettings() 
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Accessibility Settings",
                        tint = Color.White
                    )
                }
                // Reset conversation button
                IconButton(onClick = { agentViewModel.resetConversation() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = Color.White
                    )
                }
            }
        )

        // Messages list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            if (messages.isEmpty() && !isLoading && currentTypingMessage == null) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Send a command to control your device",
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Monitoring toggle explanation
                        Card(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(0.8f),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE0F7FA)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Enable the \"Monitor\" toggle to see what's happening during operations",
                                    color = Color(0xFF00796B)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "The eye icon will show you exactly what the AI is doing in real-time",
                                    color = Color(0xFF00796B),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    reverseLayout = true,
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // Show typing indicator first (will appear at the bottom)
                    if (currentTypingMessage != null) {
                        item(key = "typing") {
                            AgentMessageBubble(
                                message = currentTypingMessage!!
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    
                    // Show messages in reverse order (newest first due to reverseLayout)
                    items(
                        items = messages,
                        key = { message -> "${message.timestamp}_${message.isUserMessage}_${message.text.hashCode()}" }
                    ) { message ->
                        AgentMessageBubble(
                            message = message
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Loading Indicator
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF26A69A)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isCommand) "Executing command..." else "Processing...",
                            color = Color(0xFF26A69A)
                        )
                    }
                }
            }
        }

        // Message input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Message input field
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = { Text("Type a command like 'Open YouTube'") },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF26A69A),
                        unfocusedBorderColor = Color.LightGray,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    maxLines = 1,
                    singleLine = true
                )
                
                // Send button
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            agentViewModel.processUserInput(messageText)
                            messageText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF26A69A), CircleShape),
                    enabled = !isLoading && messageText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Send",
                        tint = Color.White
                    )
                }
            }
        }
    }
    
    // Accessibility service permission dialog
    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text("Enable Accessibility Service") },
            text = { 
                Column {
                    Text("To fully use the AI Agent's app control capabilities, you need to enable the Accessibility Service.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This allows the AI Agent to automate actions in other apps on your behalf.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showAccessibilityDialog = false
                        val appControlManager = AppControlManager(context)
                        appControlManager.openAccessibilitySettings()
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) {
                    Text("Later")
                }
            }
        )
    }
}

@Composable
fun AgentMessageBubble(
    message: ChatMessage
) {
    // Manage expanded state for long messages
    var isExpanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (message.isUserMessage) Color(0xFF26A69A) else Color(0xFFEEEEEE),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    isExpanded = !isExpanded
                }
        ) {
            if (!message.isUserMessage && message.text.contains("🤔 Thinking:") && message.text.contains("✨ Answer:")) {
                // Split the message into thinking and answer parts
                Column(modifier = Modifier.padding(12.dp)) {
                    val parts = message.text.split("✨ Answer:")
                    val thinkingPart = parts[0].trim()
                    val answerPart = if (parts.size > 1) parts[1].trim() else ""
                    
                    // Thinking part
                    Text(
                        text = thinkingPart,
                        color = Color.Black,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 8,
                        overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis
                    )
                    
                    // Divider
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color.LightGray, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Answer part
                    Text(
                        text = "✨ Answer: $answerPart",
                        color = Color.Black,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                }
            } else {
                // Regular message without reasoning format
                Text(
                    text = message.text,
                    color = if (message.isUserMessage) Color.White else Color.Black,
                    modifier = Modifier.padding(12.dp),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 12,
                    overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis
                )
            }
        }
    }
} 