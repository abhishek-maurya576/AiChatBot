package com.org.aichatbot

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.org.aichatbot.ui.screens.*
import com.org.aichatbot.ui.theme.AIChatBotTheme
import com.org.aichatbot.viewmodel.ChatViewModel
import com.org.aichatbot.service.FloatingEyeService

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 100
    }
    
    private var monitoringEnabled = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get API key from BuildConfig
        val apiKey = BuildConfig.API_KEY
        
        setContent {
            AIChatBotTheme {
                val navController = rememberNavController()
                
                // Create a shared ViewModel instance
                val chatViewModel: ChatViewModel = viewModel()
                
                // State for monitoring toggle
                var isMonitoringEnabled by remember { mutableStateOf(monitoringEnabled) }
                
                NavHost(
                    navController = navController,
                    startDestination = "welcome"
                ) {
                    // Welcome Screen
                    composable("welcome") {
                        WelcomeScreen(
                            onGetStarted = { navController.navigate("home") }
                        )
                    }
                    
                    // Home/Menu Screen
                    composable("home") {
                        HomeScreen(
                            onNavigateToChatBot = { navController.navigate("chatbot") },
                            onNavigateToAiAgent = { navController.navigate("aiagent") },
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    
                    // Chat Bot Screen
                    composable("chatbot") {
                        ChatScreen(
                            viewModel = chatViewModel,
                            apiKey = apiKey,  // Pass actual API key from BuildConfig
                            onBackToHome = { navController.popBackStack() }
                        )
                    }
                    
                    // AI Agent Screen
                    composable("aiagent") {
                        AiAgentScreen(
                            chatViewModel = chatViewModel,
                            apiKey = apiKey,  // Pass actual API key from BuildConfig
                            onBackToHome = { navController.popBackStack() },
                            isMonitoringEnabled = isMonitoringEnabled,
                            onToggleMonitoring = { enabled ->
                                if (enabled) {
                                    if (checkOverlayPermissionAndStart()) {
                                        isMonitoringEnabled = true
                                        monitoringEnabled = true
                                    }
                                } else {
                                    stopMonitoring()
                                    isMonitoringEnabled = false
                                    monitoringEnabled = false
                                }
                            }
                        )
                    }
                    
                    // Settings Screen
                    composable("settings") {
                        SettingsScreen(
                            onBackToHome = { navController.popBackStack() }
                        )
                    }
                    
                    // Chat List Screen
                    composable("chatlist") {
                        ChatListScreen(
                            onChatSelected = { chatId -> 
                                // For now, just navigate back to chat screen
                                navController.navigate("chatbot")
                            },
                            onNewChat = {
                                chatViewModel.startNewChat()
                                navController.navigate("chatbot")
                            },
                            onBackToHome = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Check if the app has overlay permission and start the floating service if it does
     * @return true if permission is granted and service started, false otherwise
     */
    private fun checkOverlayPermissionAndStart(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // Request permission
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            Toast.makeText(this, "Please grant overlay permission to enable monitoring", Toast.LENGTH_LONG).show()
            return false
        } else {
            // Start the floating service
            startMonitoring()
            return true
        }
    }
    
    /**
     * Start the floating eye monitoring service
     */
    private fun startMonitoring() {
        try {
            val intent = Intent(this, FloatingEyeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Screen monitoring activated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting monitoring service: ${e.message}")
            Toast.makeText(this, "Failed to start monitoring service", Toast.LENGTH_SHORT).show()
            monitoringEnabled = false
        }
    }
    
    /**
     * Stop the floating eye monitoring service
     */
    private fun stopMonitoring() {
        val intent = Intent(this, FloatingEyeService::class.java)
        stopService(intent)
        Toast.makeText(this, "Screen monitoring deactivated", Toast.LENGTH_SHORT).show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startMonitoring()
                monitoringEnabled = true
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
                monitoringEnabled = false
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (monitoringEnabled) {
            stopMonitoring()
        }
    }
}