package com.org.aichatbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppControlAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppControlService"
        
        // Static instance for global access
        private var instance: AppControlAccessibilityService? = null
        
        // Actions that can be performed
        const val ACTION_CLICK_NODE = "CLICK_NODE"
        const val ACTION_TYPE_TEXT = "TYPE_TEXT"
        const val ACTION_FIND_AND_CLICK = "FIND_AND_CLICK"
        
        // Status flow to track service state
        private val _serviceStatus = MutableStateFlow(false)
        val serviceStatus: StateFlow<Boolean> = _serviceStatus.asStateFlow()
        
        fun getInstance(): AppControlAccessibilityService? = instance
    }
    
    init {
        instance = this
        _serviceStatus.value = true
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _serviceStatus.value = false
        Log.d(TAG, "Service destroyed")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Log when a new app is opened
                val packageName = event.packageName?.toString()
                Log.d(TAG, "App opened: $packageName")
            }
        }
    }
    
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        FloatingEyeService.updateOperation("Finding Node", "By text: \"$text\"")
        val rootNode = rootInActiveWindow ?: return null
        return findNodeByTextRecursive(rootNode, text)
    }
    
    private fun findNodeByTextRecursive(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        // Check if this node contains the text
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }
        
        // Check child nodes
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i) ?: continue
            val found = findNodeByTextRecursive(childNode, text)
            if (found != null) {
                return found
            }
        }
        
        return null
    }
    
    // Click on a specific node
    fun clickOnNode(node: AccessibilityNodeInfo): Boolean {
        FloatingEyeService.updateOperation("Clicking", "Node: ${getNodeDescription(node)}")
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    
    // Click at specific coordinates (for newer Android versions)
    @RequiresApi(Build.VERSION_CODES.N)
    fun clickAtCoordinates(x: Float, y: Float): Boolean {
        FloatingEyeService.updateOperation("Clicking", "Coordinates: $x, $y")
        val path = Path()
        path.moveTo(x, y)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
    
    // Type text into a focused field
    fun typeText(text: String): Boolean {
        FloatingEyeService.updateOperation("Typing Text", "Text: \"$text\"")
        val arguments = Bundle().apply {
            putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        
        val focusedNode = findFocusedEditText() ?: return false
        return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
    
    // Perform an action on a node with optional arguments
    fun performAction(node: AccessibilityNodeInfo, action: Int, arguments: Bundle? = null): Boolean {
        val actionName = getActionName(action)
        FloatingEyeService.updateOperation("Performing Action", "$actionName on ${getNodeDescription(node)}")
        return node.performAction(action, arguments)
    }
    
    // Clear text from a field by selecting all and deleting
    fun clearTextField(node: AccessibilityNodeInfo): Boolean {
        FloatingEyeService.updateOperation("Clearing Text", "Node: ${getNodeDescription(node)}")
        // First focus the node
        if (!node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            Log.e(TAG, "Failed to focus on text field")
            return false
        }
        Thread.sleep(300)
        
        // Select all text - using the proper constant
        if (!node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {  // Long click usually triggers select all
            Log.e(TAG, "Failed to long click for text selection")
            return false
        }
        Thread.sleep(300)
        
        // Clear the field
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        })
    }
    
    // Set text directly to a node without typing it character by character
    fun setTextToNode(node: AccessibilityNodeInfo, text: String): Boolean {
        FloatingEyeService.updateOperation("Setting Text", "\"$text\" to ${getNodeDescription(node)}")
        // First focus the node
        if (!node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            Log.e(TAG, "Failed to focus on text field")
            return false
        }
        Thread.sleep(300)
        
        // Set the text
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
    
    // Clear and set text to a node - useful for ensuring the field is empty before setting new text
    fun clearAndSetText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!clearTextField(node)) {
            Log.e(TAG, "Failed to clear text field")
            return false
        }
        Thread.sleep(300)
        
        return setTextToNode(node, text)
    }
    
    // Find and return the currently focused edit text field
    fun findFocusedEditText(): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        
        // Try to find a node with input focus
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode?.isEditable == true) {
            return focusedNode
        }
        
        // If not found, try to search for editable fields
        return findEditableNode(rootNode)
    }
    
    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i) ?: continue
            val editableNode = findEditableNode(childNode)
            if (editableNode != null) {
                return editableNode
            }
        }
        
        return null
    }
    
    // Find a node by content description
    fun findNodeByContentDescription(description: String): AccessibilityNodeInfo? {
        FloatingEyeService.updateOperation("Finding Node", "By description: \"$description\"")
        val rootNode = rootInActiveWindow ?: return null
        return findNodeByContentDescriptionRecursive(rootNode, description)
    }
    
    private fun findNodeByContentDescriptionRecursive(node: AccessibilityNodeInfo, description: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(description, ignoreCase = true) == true) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i) ?: continue
            val found = findNodeByContentDescriptionRecursive(childNode, description)
            if (found != null) {
                return found
            }
        }
        
        return null
    }
    
    // Find and click on a node containing specific text
    fun findAndClickByText(text: String): Boolean {
        val node = findNodeByText(text) ?: return false
        return clickOnNode(node)
    }
    
    // Helper to get a readable description of a node
    private fun getNodeDescription(node: AccessibilityNodeInfo): String {
        val className = node.className?.toString()?.substringAfterLast('.') ?: "Unknown"
        val text = node.text?.toString()?.take(20) ?: ""
        val contentDesc = node.contentDescription?.toString()?.take(20) ?: ""
        
        return "$className ${if (text.isNotEmpty()) "\"$text\"" else ""} ${if (contentDesc.isNotEmpty()) "($contentDesc)" else ""}"
    }
    
    // Helper to get action name
    private fun getActionName(action: Int): String {
        return when (action) {
            AccessibilityNodeInfo.ACTION_CLICK -> "Click"
            AccessibilityNodeInfo.ACTION_LONG_CLICK -> "Long Click"
            AccessibilityNodeInfo.ACTION_FOCUS -> "Focus"
            AccessibilityNodeInfo.ACTION_SET_TEXT -> "Set Text"
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> "Scroll Forward"
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> "Scroll Backward"
            else -> "Action #$action"
        }
    }
} 