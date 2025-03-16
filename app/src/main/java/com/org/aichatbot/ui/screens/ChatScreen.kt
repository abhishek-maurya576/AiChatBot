package com.org.aichatbot.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.org.aichatbot.model.ChatMessage
import com.org.aichatbot.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "ChatScreen"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    apiKey: String,
    onBackToHome: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.initializeGeminiService(apiKey)
    }

    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentTypingMessage by viewModel.currentTypingMessage.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // Dialog state for history
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Sound and haptic feedback for touch interactions
    val interactionSource = remember { MutableInteractionSource() }

    // Animated welcome text
    var showWelcomeMessage by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3000)
        showWelcomeMessage = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top App Bar with animation
        TopAppBar(
            title = { 
                Text("Chat Bot")
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF26A69A),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            ),
            navigationIcon = {
                IconButton(
                    onClick = onBackToHome,
                    modifier = Modifier.scale(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.startNewChat() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New chat",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { showHistoryDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = Color.White
                    )
                }
            }
        )

        // Messages list with animation
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            if (messages.isEmpty() && !isLoading && currentTypingMessage == null) {
                // Empty state with animation
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (showWelcomeMessage) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "👋 Welcome to Chat Bot!",
                                color = Color(0xFF26A69A),
                                fontSize = 20.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Send a message to start chatting",
                                color = Color.Gray
                            )
                        }
                    } else {
                        Text(
                            text = "Send a message to start chatting",
                            color = Color.Gray
                        )
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
                    // Show typing indicator with animation
                    if (currentTypingMessage != null) {
                        item(key = "typing") {
                            TypingIndicator(message = currentTypingMessage!!)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    
                    // Show messages with animations
                    items(
                        items = messages,
                        key = { message -> "${message.timestamp}_${message.isUserMessage}_${message.text.hashCode()}" }
                    ) { message ->
                        AnimatedMessageBubble(message = message)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Loading Indicator with animation
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
                            color = Color(0xFF26A69A),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PulsatingText(text = "Thinking...")
                    }
                }
            }
        }

        // Message input with animations
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
                // Message input field with animation
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = { Text("Type Your Message") },
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
                
                // Send button with animation
                Box {
                    val buttonColor = if (!isLoading && messageText.isNotBlank()) 
                        Color(0xFF26A69A) else Color(0xFFBDBDBD)
                    
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(messageText)
                                messageText = ""
                                coroutineScope.launch {
                                    // Scroll to top after sending message
                                    listState.animateScrollToItem(0)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = buttonColor,
                                shape = CircleShape
                            ),
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
    }
    
    // History Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Chat History") },
            text = { 
                Column {
                    Text("Your recent conversations will appear here.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This feature stores your chat history for easy access to previous conversations.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Close", color = Color(0xFF26A69A))
                }
            },
            containerColor = Color.White,
            titleContentColor = Color(0xFF26A69A),
            textContentColor = Color.Black
        )
    }
}

@Composable
fun AnimatedMessageBubble(message: ChatMessage) {
    // Manage expanded state for long messages
    var isExpanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (message.isUserMessage) Alignment.End else Alignment.Start
        ) {
            // Sender label
            Text(
                text = if (message.isUserMessage) "You" else "AI Assistant",
                color = if (message.isUserMessage) Color(0xFF26A69A) else Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
            
            // Message bubble
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
            
            // Timestamp
            Text(
                text = formatTimestamp(message.timestamp),
                color = Color.Gray,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun TypingIndicator(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "AI Assistant",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
            
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFEEEEEE),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thinking",
                        color = Color.Black
                    )
                    
                    Row {
                        for (i in 0..2) {
                            val delay = i * 300
                            val infiniteTransition = rememberInfiniteTransition(label = "dot$i")
                            val size by infiniteTransition.animateFloat(
                                initialValue = 5f,
                                targetValue = 8f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(500, delayMillis = delay),
                                    repeatMode = RepeatMode.Reverse
                                ), 
                                label = "dot$i size"
                            )
                            
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(size.dp)
                                    .background(Color.DarkGray, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PulsatingText(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ), 
        label = "pulse scale"
    )
    
    Text(
        text = text,
        color = Color(0xFF26A69A),
        modifier = Modifier.scale(scale),
        fontSize = 16.sp
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}