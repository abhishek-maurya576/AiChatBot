package com.org.aichatbot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackToHome: () -> Unit
) {
    var selectedTheme by remember { mutableStateOf("System Adaptive") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Settings") },
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
            }
        )
        
        // Settings Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Theme Section
            Text(
                text = "Theme",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEEEEEE)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    // Light Theme Option
                    ThemeOption(
                        title = "Light",
                        selected = selectedTheme == "Light",
                        onSelect = { selectedTheme = "Light" }
                    )
                    
                    Divider(color = Color.LightGray, thickness = 1.dp)
                    
                    // Dark Theme Option
                    ThemeOption(
                        title = "Dark",
                        selected = selectedTheme == "Dark",
                        onSelect = { selectedTheme = "Dark" }
                    )
                    
                    Divider(color = Color.LightGray, thickness = 1.dp)
                    
                    // System Adaptive Option
                    ThemeOption(
                        title = "System Adaptive",
                        selected = selectedTheme == "System Adaptive",
                        onSelect = { selectedTheme = "System Adaptive" }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App Version Section
            Text(
                text = "App Version",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEEEEEE)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "V 1.0.0",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        
        if (selected) {
            RadioButton(
                selected = true,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF26A69A)
                )
            )
        }
    }
} 