package com.org.aichatbot.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import android.os.Bundle

/**
 * Manager class to handle app control operations like opening apps,
 * controlling system settings, and performing common actions
 */
class AppControlManager(private val context: Context) {

    companion object {
        private const val TAG = "AppControlManager"
        
        // Common package names
        private val APP_PACKAGES = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "instagram" to "com.instagram.android",
            "settings" to "com.android.settings",
            "camera" to "com.android.camera2",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "photos" to "com.google.android.apps.photos",
            "play store" to "com.android.vending",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient"
        )
    }
    
    /**
     * Open an app using its name
     * @param appName The name of the app to open
     * @return True if the app was found and an attempt was made to open it
     */
    fun openApp(appName: String): Boolean {
        val packageName = getPackageNameForApp(appName)
        if (packageName.isNotEmpty()) {
            return openAppByPackage(packageName)
        }
        Log.d(TAG, "No package found for app: $appName")
        return false
    }
    
    /**
     * Get package name for an app by its common name
     */
    private fun getPackageNameForApp(appName: String): String {
        val normalizedName = appName.lowercase().trim()
        
        // Check in our predefined map
        APP_PACKAGES.forEach { (name, packageName) ->
            if (normalizedName.contains(name)) {
                return packageName
            }
        }
        
        // Try to find by querying installed packages
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        
        for (app in installedApps) {
            val appInfo = packageManager.getApplicationLabel(app).toString()
            if (appInfo.lowercase().contains(normalizedName)) {
                return app.packageName
            }
        }
        
        return ""
    }
    
    /**
     * Open an app by its package name
     */
    fun openAppByPackage(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Opened app with package: $packageName")
                true
            } else {
                Log.e(TAG, "No launch intent for package: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app: ${e.message}")
            false
        }
    }
    
    /**
     * Sends a WhatsApp message to a contact or phone number
     */
    fun sendWhatsAppMessage(contactOrPhone: String, message: String): Boolean {
        Log.d(TAG, "Starting WhatsApp message send to: $contactOrPhone with message: $message")
        FloatingEyeService.updateOperation("WhatsApp Message", "To: $contactOrPhone")
        
        // Check if contactOrPhone is a phone number
        val isPhoneNumber = contactOrPhone.matches(Regex("\\d{10}|\\+\\d{12,13}"))
        
        if (isPhoneNumber) {
            Log.d(TAG, "Detected phone number format, opening WhatsApp directly")
            return openWhatsAppDirectly(contactOrPhone, message)
        }
        
        // It's a contact name, try to find it in WhatsApp
        Log.d(TAG, "Attempting to find contact: $contactOrPhone in WhatsApp")
        
        try {
            // Launch WhatsApp
            val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (intent == null) {
                Log.e(TAG, "WhatsApp not installed")
                Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                return false
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            
            // Wait for WhatsApp to open
            Thread.sleep(2000)
            
            val accessibilityService = AppControlAccessibilityService.getInstance() ?: return false
            val screenHeight = context.resources.displayMetrics.heightPixels
            val screenWidth = context.resources.displayMetrics.widthPixels
            
            // Try to find the "New chat" button - multiple approaches
            var success = false
            
            // 1. Look for the floating action button with content description "New chat"
            val newChatNode = accessibilityService.findNodeByContentDescription("New chat")
            if (newChatNode != null) {
                Log.d(TAG, "Found New chat button by content description")
                accessibilityService.clickOnNode(newChatNode)
                Thread.sleep(1500)
                success = true
            }
            
            // 2. If not found, look for a FAB (floating action button) at the bottom of the screen
            if (!success) {
                val rootNode = accessibilityService.rootInActiveWindow ?: return false
                val fabNodes = findAllClickableNodes(rootNode)
                
                for (node in fabNodes) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    
                    // FABs are usually at the bottom right
                    if (bounds.bottom > (screenHeight * 0.7) && bounds.right > (screenWidth * 0.7)) {
                        Log.d(TAG, "Found potential FAB button at bottom right, clicking it")
                        accessibilityService.clickOnNode(node)
                        Thread.sleep(1500)
                        success = true
                        break
                    }
                }
            }
            
            // 3. If still not found, try looking for "New chat" text
            if (!success) {
                val newChatTextNode = accessibilityService.findNodeByText("New chat")
                if (newChatTextNode != null) {
                    Log.d(TAG, "Found New chat by text")
                    accessibilityService.clickOnNode(newChatTextNode)
                    Thread.sleep(1500)
                    success = true
                }
            }
            
            if (!success) {
                Log.e(TAG, "Could not find New chat button")
                // Try to continue anyway, we might already be on a search screen
            }
            
            // Find and interact with the search field
            Thread.sleep(1000) // Wait for the search view to appear
            
            // Find the search field - could be "Search..." or other text
            var searchField = accessibilityService.findNodeByContentDescription("Search...")
            
            if (searchField == null) {
                searchField = accessibilityService.findNodeByText("Search...")
            }
            
            if (searchField == null) {
                // Try to find an EditText field
                val rootNode = accessibilityService.rootInActiveWindow ?: return false
                searchField = findFirstEditTextNode(rootNode)
                
                if (searchField == null) {
                    // Try to find any text field at the top of the screen
                    val allNodes = findAllNodes(rootNode)
                    for (node in allNodes) {
                        if (node.isEditable) {
                            val bounds = Rect()
                            node.getBoundsInScreen(bounds)
                            
                            // Check if the node is near the top of the screen
                            if (bounds.top < (screenHeight * 0.2)) {
                                searchField = node
                                break
                            }
                        }
                    }
                }
            }
            
            if (searchField != null) {
                Log.d(TAG, "Found search field, clicking it")
                accessibilityService.clickOnNode(searchField)
                Thread.sleep(500)
                
                // Type the contact name
                Log.d(TAG, "Typing contact name: $contactOrPhone")
                accessibilityService.typeText(contactOrPhone)
                Thread.sleep(1500)
                
                // Now try multiple strategies to find and click the contact
                var contactFound = false
                
                // Strategy 1: Find exact match by text
                val exactMatchNode = accessibilityService.findNodeByText(contactOrPhone)
                if (exactMatchNode != null) {
                    Log.d(TAG, "Found exact match for contact: $contactOrPhone")
                    accessibilityService.clickOnNode(exactMatchNode)
                    contactFound = true
                }
                
                // Strategy 2: Find by partial name match
                if (!contactFound) {
                    val names = contactOrPhone.split(" ")
                    for (name in names) {
                        if (name.length > 2) { // Avoid matching on "to", "a", etc.
                            val partialMatchNode = accessibilityService.findNodeByText(name)
                            if (partialMatchNode != null) {
                                Log.d(TAG, "Found partial match for contact using name part: $name")
                                accessibilityService.clickOnNode(partialMatchNode)
                                contactFound = true
                                break
                            }
                        }
                    }
                }
                
                // Strategy 3: Click on the first list item
                if (!contactFound) {
                    Thread.sleep(1000) // Wait for search results
                    val rootNode = accessibilityService.rootInActiveWindow ?: return false
                    
                    // Try to find a ListView or RecyclerView
                    val listNode = findListViewNode(rootNode)
                    if (listNode != null) {
                        // Get the first child
                        if (listNode.childCount > 0) {
                            val firstItem = listNode.getChild(0)
                            if (firstItem != null) {
                                Log.d(TAG, "Clicking on first list item")
                                accessibilityService.clickOnNode(firstItem)
                                contactFound = true
                            }
                        }
                    }
                }
                
                // Strategy 4: Look for any clickable item after search
                if (!contactFound) {
                    Thread.sleep(1000)
                    val rootNode = accessibilityService.rootInActiveWindow ?: return false
                    val clickableNodes = findAllClickableNodes(rootNode)
                    
                    // Filter for nodes that might be contact items (typically in the middle of the screen)
                    for (node in clickableNodes) {
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        
                        // Check if node is in the middle section of the screen
                        if (bounds.top > (screenHeight * 0.2) && bounds.bottom < (screenHeight * 0.8)) {
                            // If this node has text that contains any part of the contact name, it's likely our target
                            val nodeText = getNodeText(node).lowercase()
                            
                            // Check if the node text contains any part of the contact name
                            val nameParts = contactOrPhone.lowercase().split(" ")
                            for (part in nameParts) {
                                if (part.length > 2 && nodeText.contains(part)) {
                                    Log.d(TAG, "Found node with matching text part: $part")
                                    accessibilityService.clickOnNode(node)
                                    contactFound = true
                                    break
                                }
                            }
                            
                            if (contactFound) break
                        }
                    }
                    
                    // If still not found, just click the first clickable item that's not at the top or bottom
                    if (!contactFound && clickableNodes.isNotEmpty()) {
                        for (node in clickableNodes) {
                            val bounds = Rect()
                            node.getBoundsInScreen(bounds)
                            
                            // Check if node is in the middle section of the screen
                            if (bounds.top > (screenHeight * 0.2) && bounds.bottom < (screenHeight * 0.8)) {
                                Log.d(TAG, "Clicking first middle clickable item as fallback")
                                accessibilityService.clickOnNode(node)
                                contactFound = true
                                break
                            }
                        }
                    }
                }
                
                if (!contactFound) {
                    Log.e(TAG, "Could not find the contact: $contactOrPhone")
                    Toast.makeText(context, "Could not find contact: $contactOrPhone", Toast.LENGTH_SHORT).show()
                    return false
                }
                
                // Wait for the chat screen to load
                Thread.sleep(1500)
                
                // Find the message input field - look for "Type a message" or similar
                val messageField = accessibilityService.findNodeByContentDescription("Type a message") 
                    ?: accessibilityService.findNodeByText("Type a message")
                    ?: findFirstEditTextNode(accessibilityService.rootInActiveWindow ?: return false)
                
                if (messageField != null) {
                    Log.d(TAG, "Found message field, clicking it")
                    accessibilityService.clickOnNode(messageField)
                    Thread.sleep(500)
                    
                    // Type the message
                    Log.d(TAG, "Typing message: $message")
                    accessibilityService.typeText(message)
                    Thread.sleep(500)
                    
                    // Find and click the send button
                    val sendButton = accessibilityService.findNodeByContentDescription("Send")
                    if (sendButton != null) {
                        Log.d(TAG, "Found send button, clicking it")
                        accessibilityService.clickOnNode(sendButton)
                        Thread.sleep(500)
                        return true
                    } else {
                        // Try to find the send button by looking for a button at the right side
                        val rootNode = accessibilityService.rootInActiveWindow ?: return false
                        val clickableNodes = findAllClickableNodes(rootNode)
                        
                        for (node in clickableNodes) {
                            val bounds = Rect()
                            node.getBoundsInScreen(bounds)
                            
                            // Send button is usually at the bottom right
                            if (bounds.bottom > (screenHeight * 0.7) && bounds.right > (screenWidth * 0.7)) {
                                Log.d(TAG, "Found potential send button at bottom right, clicking it")
                                accessibilityService.clickOnNode(node)
                                Thread.sleep(500)
                                return true
                            }
                        }
                        
                        Log.e(TAG, "Could not find send button")
                        return false
                    }
                } else {
                    Log.e(TAG, "Could not find message input field")
                    return false
                }
            } else {
                Log.e(TAG, "Could not find search field")
                dumpAccessibilityHierarchy(accessibilityService.rootInActiveWindow)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WhatsApp message: ${e.message}")
            e.printStackTrace()
            return false
        }
        
        return false
    }
    
    /**
     * Helper method to dump the accessibility hierarchy for debugging
     */
    private fun dumpAccessibilityHierarchy(node: AccessibilityNodeInfo?) {
        if (node == null) {
            Log.d(TAG, "Node is null")
            return
        }
        
        FloatingEyeService.updateOperation("Dumping Hierarchy", "Analyzing screen structure")
        dumpNode(node, 0)
    }
    
    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int) {
        val indent = " ".repeat(depth * 2)
        val className = node.className ?: "null"
        val text = node.text ?: "null"
        val contentDesc = node.contentDescription ?: "null"
        val clickable = node.isClickable
        val editable = node.isEditable
        
        Log.d(TAG, "$indent Node: class=$className, text=$text, contentDesc=$contentDesc, clickable=$clickable, editable=$editable")
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNode(child, depth + 1)
            child.recycle()
        }
    }
    
    /**
     * Helper method to find a ListView or RecyclerView
     */
    private fun findListViewNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val listNodes = ArrayList<AccessibilityNodeInfo>()
        findNodesByClassName(rootNode, "android.widget.ListView", listNodes)
        if (listNodes.isEmpty()) {
            findNodesByClassName(rootNode, "androidx.recyclerview.widget.RecyclerView", listNodes)
        }
        
        return if (listNodes.isNotEmpty()) listNodes[0] else null
    }
    
    /**
     * Helper method to find nodes by class name
     */
    private fun findNodesByClassName(node: AccessibilityNodeInfo, className: String, results: ArrayList<AccessibilityNodeInfo>) {
        if (node.className?.toString() == className) {
            results.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByClassName(child, className, results)
        }
    }
    
    /**
     * Helper method to find an EditText node
     */
    private fun findFirstEditTextNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (rootNode.isEditable) {
            return rootNode
        }
        
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val result = findFirstEditTextNode(child)
            if (result != null) {
                return result
            }
        }
        
        return null
    }
    
    /**
     * Helper method to find all nodes in the hierarchy
     */
    private fun findAllNodes(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val results = ArrayList<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, results)
        return results
    }
    
    private fun collectAllNodes(node: AccessibilityNodeInfo, results: ArrayList<AccessibilityNodeInfo>) {
        results.add(node)
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllNodes(child, results)
        }
    }
    
    /**
     * Helper method to find all clickable nodes
     */
    private fun findAllClickableNodes(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val results = ArrayList<AccessibilityNodeInfo>()
        collectClickableNodes(rootNode, results)
        return results
    }
    
    private fun collectClickableNodes(node: AccessibilityNodeInfo, results: ArrayList<AccessibilityNodeInfo>) {
        if (node.isClickable) {
            results.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickableNodes(child, results)
        }
    }
    
    /**
     * Open YouTube with a search query
     */
    fun openYouTubeSearch(query: String): Boolean {
        return try {
            val encodedQuery = Uri.encode(query)
            val uri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
            
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Opening YouTube with search: $query")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening YouTube search: ${e.message}")
            false
        }
    }
    
    /**
     * Open a URL in the default browser
     */
    fun openWebsite(url: String): Boolean {
        return try {
            var formattedUrl = url
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                formattedUrl = "https://$url"
            }
            
            val uri = Uri.parse(formattedUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Opening URL: $formattedUrl")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening URL: ${e.message}")
            false
        }
    }
    
    /**
     * Open Google search with a query
     */
    fun openGoogleSearch(query: String): Boolean {
        return try {
            val encodedQuery = Uri.encode(query)
            val uri = Uri.parse("https://www.google.com/search?q=$encodedQuery")
            
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Opening Google search: $query")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Google search: ${e.message}")
            false
        }
    }
    
    /**
     * Toggle Wi-Fi state
     * Note: On Android 10+ this requires panel interaction
     */
    fun toggleWiFi(enable: Boolean): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // For Android 9 and below
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiManager.isWifiEnabled = enable
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling WiFi: ${e.message}")
                false
            }
        } else {
            // For Android 10+, direct toggle not allowed - open settings panel
            try {
                val intent = Intent(Settings.Panel.ACTION_WIFI)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error opening WiFi settings: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Toggle Bluetooth state
     * Note: On Android 12+ this requires panel interaction
     */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun toggleBluetooth(enable: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // For Android 12+, direct toggle not allowed - open Bluetooth settings
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                // For Android 11 and below
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bluetoothAdapter = bluetoothManager.adapter
                
                if (bluetoothAdapter != null) {
                    if (enable) {
                        bluetoothAdapter.enable()
                    } else {
                        bluetoothAdapter.disable()
                    }
                    true
                } else {
                    Log.e(TAG, "Bluetooth adapter not available")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling Bluetooth: ${e.message}")
            false
        }
    }
    
    /**
     * Make a phone call
     */
    fun makePhoneCall(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$phoneNumber")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Dialing phone number: $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error making phone call: ${e.message}")
            false
        }
    }
    
    /**
     * Send an email
     */
    fun sendEmail(emailAddress: String, subject: String, body: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:")
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
            intent.putExtra(Intent.EXTRA_SUBJECT, subject)
            intent.putExtra(Intent.EXTRA_TEXT, body)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Opening email to: $emailAddress")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email: ${e.message}")
            false
        }
    }
    
    /**
     * Opens Google Maps with a location search
     */
    fun openMapsWithLocation(location: String): Boolean {
        return try {
            val encodedLocation = Uri.encode(location)
            val uri = Uri.parse("geo:0,0?q=$encodedLocation")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Opening Maps with location: $location")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Maps: ${e.message}")
            false
        }
    }
    
    /**
     * Check if the accessibility service is enabled
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return AppControlAccessibilityService.getInstance() != null
    }
    
    /**
     * Open accessibility settings to enable the service
     */
    fun openAccessibilitySettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            
            Toast.makeText(
                context, 
                "Please enable 'AI Agent' in accessibility settings", 
                Toast.LENGTH_LONG
            ).show()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings: ${e.message}")
            false
        }
    }
    
    /**
     * Opens WhatsApp directly with a message for a phone number
     */
    private fun openWhatsAppDirectly(phoneNumber: String, message: String): Boolean {
        return try {
            // Format the phone number by removing any spaces or special characters
            var formattedNumber = phoneNumber.replace("\\s+".toRegex(), "")
            
            // Add country code if it's a 10-digit number (assuming Indian format for example)
            if (formattedNumber.matches("\\d{10}".toRegex())) {
                formattedNumber = "91$formattedNumber" // Add Indian country code
            }
            
            // WhatsApp API URL format
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber&text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            
            Log.d(TAG, "Opening WhatsApp directly with phone: $formattedNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening WhatsApp directly: ${e.message}")
            false
        }
    }
    
    /**
     * Recursively get text from a node and its children
     */
    private fun getNodeText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        
        // Add the node's text if not null
        if (node.text != null) {
            sb.append(node.text)
        }
        
        // Add content description if available
        if (node.contentDescription != null) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(node.contentDescription)
        }
        
        // Recursively add text from children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childText = getNodeText(child)
            if (childText.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(childText)
            }
        }
        
        return sb.toString()
    }
    
    /**
     * Sends an SMS message to a contact or phone number
     */
    fun sendSmsMessage(contactOrPhone: String, message: String): Boolean {
        Log.d(TAG, "Starting SMS message send to: $contactOrPhone with message: $message")
        FloatingEyeService.updateOperation("SMS Message", "To: $contactOrPhone")
        
        try {
            // Check if it's a phone number
            val isPhoneNumber = contactOrPhone.matches(Regex("\\d{10}|\\+\\d{12,13}"))
            
            if (isPhoneNumber) {
                // Open the SMS app directly with the phone number
                val uri = Uri.parse("sms:$contactOrPhone")
                val intent = Intent(Intent.ACTION_SENDTO, uri)
                intent.putExtra("sms_body", message)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                
                Log.d(TAG, "Opened SMS app with number: $contactOrPhone")
                return true
            } else {
                // It's a contact name, we need to use the accessibility service
                val accessibilityService = AppControlAccessibilityService.getInstance() ?: return false
                
                // Open the messages app
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_APP_MESSAGING)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                
                Thread.sleep(2000) // Wait for app to open
                
                // Find and click on the "new message" button
                val newMessageNode = accessibilityService.findNodeByContentDescription("New message") ?:
                                    accessibilityService.findNodeByContentDescription("New conversation") ?:
                                    accessibilityService.findNodeByText("New") ?:
                                    accessibilityService.findNodeByText("+")
                
                if (newMessageNode != null) {
                    accessibilityService.clickOnNode(newMessageNode)
                    Thread.sleep(1500)
                    
                    // Find the "To" field and enter the contact name
                    val toField = accessibilityService.findNodeByContentDescription("To") ?:
                                 accessibilityService.findNodeByText("To") ?:
                                 findFirstEditTextNode(accessibilityService.rootInActiveWindow ?: return false)
                    
                    if (toField != null) {
                        accessibilityService.clickOnNode(toField)
                        Thread.sleep(500)
                        
                        // Type the contact name
                        accessibilityService.typeText(contactOrPhone)
                        Thread.sleep(1500)
                        
                        // Try to find the contact in the results
                        val contactNode = accessibilityService.findNodeByText(contactOrPhone)
                        if (contactNode != null) {
                            accessibilityService.clickOnNode(contactNode)
                            Thread.sleep(1000)
                            
                            // Find the message field
                            val messageField = findFirstEditTextNode(accessibilityService.rootInActiveWindow ?: return false)
                            if (messageField != null) {
                                accessibilityService.clickOnNode(messageField)
                                Thread.sleep(500)
                                accessibilityService.typeText(message)
                                Thread.sleep(500)
                                
                                // Find and click the send button
                                val sendButton = accessibilityService.findNodeByContentDescription("Send") ?:
                                               accessibilityService.findNodeByText("Send")
                                
                                if (sendButton != null) {
                                    accessibilityService.clickOnNode(sendButton)
                                    Thread.sleep(500)
                                    return true
                                }
                            }
                        }
                    }
                }
                
                Log.e(TAG, "Could not complete SMS sending process")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS message: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Sends a Telegram message to a contact
     */
    fun sendTelegramMessage(contactOrPhone: String, message: String): Boolean {
        Log.d(TAG, "Starting Telegram message send to: $contactOrPhone with message: $message")
        FloatingEyeService.updateOperation("Telegram Message", "To: $contactOrPhone")
        
        try {
            // Check if Telegram is installed - try multiple package names as Telegram has several variants
            val telegramPackages = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus")
            var telegramPackage = ""
            
            for (pkg in telegramPackages) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    telegramPackage = pkg
                    break
                }
            }
            
            if (telegramPackage.isEmpty()) {
                Log.e(TAG, "Telegram not installed")
                Toast.makeText(context, "Telegram is not installed", Toast.LENGTH_SHORT).show()
                return false
            }
            
            // Launch Telegram
            val intent = context.packageManager.getLaunchIntentForPackage(telegramPackage)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            
            // Wait for Telegram to open
            Thread.sleep(3000) // Give it a bit more time to fully load
            
            val accessibilityService = AppControlAccessibilityService.getInstance() ?: return false
            val screenHeight = context.resources.displayMetrics.heightPixels
            val screenWidth = context.resources.displayMetrics.widthPixels
            
            // Dump the hierarchy for debugging
            Log.d(TAG, "Dumping initial Telegram UI hierarchy")
            dumpAccessibilityHierarchy(accessibilityService.rootInActiveWindow)
            
            // Look for the search button with multiple methods
            Log.d(TAG, "Looking for search button in Telegram")
            var searchNode = accessibilityService.findNodeByContentDescription("Search")
            
            if (searchNode == null) {
                searchNode = accessibilityService.findNodeByText("Search")
            }
            
            if (searchNode == null) {
                // Look for a magnifying glass icon or search-like UI element
                val rootNode = accessibilityService.rootInActiveWindow ?: return false
                val clickableNodes = findAllClickableNodes(rootNode)
                
                // Try to find clickable elements at the top of the screen (where search usually is)
                for (node in clickableNodes) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    
                    // Search is typically at the top right
                    if (bounds.top < (screenHeight * 0.15) && bounds.right > (screenWidth * 0.7)) {
                        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                        if (desc.contains("search") || getNodeText(node).lowercase().contains("search")) {
                            searchNode = node
                            Log.d(TAG, "Found potential search button by position and text")
                            break
                        }
                    }
                }
            }
            
            if (searchNode != null) {
                Log.d(TAG, "Clicking on Telegram search button")
                accessibilityService.clickOnNode(searchNode)
                Thread.sleep(1500) // Give search UI time to appear
                
                // Find the search input field
                val rootNode = accessibilityService.rootInActiveWindow ?: return false
                val searchField = findFirstEditTextNode(rootNode)
                
                if (searchField != null) {
                    Log.d(TAG, "Found and clicking on search field")
                    accessibilityService.clickOnNode(searchField)
                    Thread.sleep(800)
                    
                    // IMPORTANT: Clear any existing text first by selecting all and deleting
                    Log.d(TAG, "Clearing any existing text in search field")
                    accessibilityService.clearTextField(searchField)
                    Thread.sleep(800)
                    
                    // Type the contact name using the improved method
                    Log.d(TAG, "Setting contact name: '$contactOrPhone'")
                    accessibilityService.setTextToNode(searchField, contactOrPhone)
                    Thread.sleep(2000) // Allow more time for search results
                    
                    // Verify what text was actually entered
                    val currentSearchText = searchField.text?.toString() ?: ""
                    Log.d(TAG, "Current text in search field: '$currentSearchText'")
                    
                    // If the field doesn't contain what we expect, try again with typing
                    if (!currentSearchText.contains(contactOrPhone, ignoreCase = true)) {
                        Log.d(TAG, "Search field doesn't contain expected contact name, retrying with typing")
                        accessibilityService.clearTextField(searchField)
                        Thread.sleep(800)
                        
                        // Try typing character by character as a fallback
                        accessibilityService.clickOnNode(searchField)
                        Thread.sleep(500)
                        accessibilityService.typeText(contactOrPhone)
                        Thread.sleep(1500)
                    }
                    
                    // Dump the hierarchy again to see search results
                    Log.d(TAG, "Dumping Telegram search results hierarchy")
                    dumpAccessibilityHierarchy(accessibilityService.rootInActiveWindow)
                    
                    // Try multiple strategies to find the contact
                    var contactFound = false
                    
                    // Strategy 1: Exact match
                    val contactNode = accessibilityService.findNodeByText(contactOrPhone)
                    if (contactNode != null) {
                        Log.d(TAG, "Found exact contact match for: $contactOrPhone")
                        accessibilityService.clickOnNode(contactNode)
                        contactFound = true
                    }
                    
                    // Strategy 2: Partial match (try each word of the contact name)
                    if (!contactFound) {
                        val nameParts = contactOrPhone.split(" ")
                        for (part in nameParts) {
                            if (part.length > 2) { // Skip very short words
                                val partialNode = accessibilityService.findNodeByText(part)
                                if (partialNode != null) {
                                    Log.d(TAG, "Found partial contact match with: $part")
                                    accessibilityService.clickOnNode(partialNode)
                                    contactFound = true
                                    break
                                }
                            }
                        }
                    }
                    
                    // Strategy 3: Look for clickable items in a list
                    if (!contactFound) {
                        // Try to find a ListView or RecyclerView that would contain contacts
                        val listNode = findListViewNode(accessibilityService.rootInActiveWindow ?: return false)
                        if (listNode != null && listNode.childCount > 0) {
                            Log.d(TAG, "Found list with ${listNode.childCount} items, clicking first item")
                            val firstItem = listNode.getChild(0)
                            if (firstItem != null) {
                                accessibilityService.clickOnNode(firstItem)
                                contactFound = true
                            }
                        }
                    }
                    
                    // Strategy 4: Click anything that looks clickable in the results area
                    if (!contactFound) {
                        val newRootNode = accessibilityService.rootInActiveWindow ?: return false
                        val clickableNodes = findAllClickableNodes(newRootNode)
                        
                        for (node in clickableNodes) {
                            val bounds = Rect()
                            node.getBoundsInScreen(bounds)
                            
                            // Look for nodes in the middle of the screen (where results would be)
                            if (bounds.top > (screenHeight * 0.2) && bounds.bottom < (screenHeight * 0.8)) {
                                val nodeText = getNodeText(node).lowercase()
                                Log.d(TAG, "Checking clickable node with text: $nodeText")
                                
                                // Either the node contains part of the contact name, or we're desperate and just click
                                if (contactOrPhone.split(" ").any { part -> 
                                    part.length > 2 && nodeText.contains(part.lowercase()) 
                                }) {
                                    Log.d(TAG, "Found node matching part of contact name, clicking it")
                                    accessibilityService.clickOnNode(node)
                                    contactFound = true
                                    break
                                }
                            }
                        }
                        
                        // If still not found, try clicking the first result in the middle area
                        if (!contactFound && clickableNodes.isNotEmpty()) {
                            for (node in clickableNodes) {
                                val bounds = Rect()
                                node.getBoundsInScreen(bounds)
                                
                                if (bounds.top > (screenHeight * 0.2) && bounds.bottom < (screenHeight * 0.8)) {
                                    Log.d(TAG, "Clicking first available result item as fallback")
                                    accessibilityService.clickOnNode(node)
                                    contactFound = true
                                    break
                                }
                            }
                        }
                    }
                    
                    if (contactFound) {
                        Thread.sleep(1500) // Wait for chat to open
                        
                        // Look for the message input field
                        Log.d(TAG, "Looking for message input field in Telegram chat")
                        val newRootNode = accessibilityService.rootInActiveWindow ?: return false
                        dumpAccessibilityHierarchy(newRootNode) // Debug the chat screen
                        
                        // Try multiple approaches to find the message field
                        var messageField = accessibilityService.findNodeByContentDescription("Message")
                        
                        if (messageField == null) {
                            // Look for edit text fields
                            messageField = findFirstEditTextNode(newRootNode)
                        }
                        
                        if (messageField == null) {
                            // Try to find by common hint texts
                            val hintTexts = listOf("Message", "Write a message", "Type a message")
                            for (hint in hintTexts) {
                                val node = accessibilityService.findNodeByText(hint)
                                if (node != null) {
                                    messageField = node
                                    break
                                }
                            }
                        }
                        
                        if (messageField == null) {
                            // Look for any editable field at the bottom of the screen
                            val allNodes = findAllNodes(newRootNode)
                            for (node in allNodes) {
                                if (node.isEditable) {
                                    val bounds = Rect()
                                    node.getBoundsInScreen(bounds)
                                    
                                    // Message fields are usually at the bottom
                                    if (bounds.bottom > (screenHeight * 0.7)) {
                                        messageField = node
                                        Log.d(TAG, "Found potential message field at bottom of screen")
                                        break
                                    }
                                }
                            }
                        }
                        
                        if (messageField != null) {
                            Log.d(TAG, "Found message field, clicking it")
                            accessibilityService.clickOnNode(messageField)
                            Thread.sleep(800)
                            
                            // Clear any existing text using the improved method
                            Log.d(TAG, "Clearing message field")
                            accessibilityService.clearTextField(messageField)
                            Thread.sleep(800)
                            
                            // Type the message using the improved method
                            Log.d(TAG, "Setting message: '$message'")
                            accessibilityService.setTextToNode(messageField, message)
                            Thread.sleep(800)
                            
                            // Find and click the send button
                            var sendButton = accessibilityService.findNodeByContentDescription("Send") ?:
                                           accessibilityService.findNodeByContentDescription("Send message")
                            
                            if (sendButton == null) {
                                sendButton = accessibilityService.findNodeByText("Send")
                            }
                            
                            if (sendButton == null) {
                                // Look for clickable items at bottom right (common send button location)
                                val finalRootNode = accessibilityService.rootInActiveWindow ?: return false
                                val clickableNodes = findAllClickableNodes(finalRootNode)
                                
                                for (node in clickableNodes) {
                                    val bounds = Rect()
                                    node.getBoundsInScreen(bounds)
                                    
                                    // Send buttons are typically at the bottom right
                                    if (bounds.bottom > (screenHeight * 0.7) && bounds.right > (screenWidth * 0.7)) {
                                        sendButton = node
                                        Log.d(TAG, "Found potential send button at bottom right")
                                        break
                                    }
                                }
                            }
                            
                            if (sendButton != null) {
                                Log.d(TAG, "Found send button, clicking it")
                                accessibilityService.clickOnNode(sendButton)
                                Thread.sleep(800)
                                
                                // Try to verify the message was sent by looking for it in the chat
                                val chatRootNode = accessibilityService.rootInActiveWindow ?: return true // Assume success if we can't verify
                                val messageWords = message.split(" ")
                                
                                // Look for any substantial word from our message in the chat
                                for (word in messageWords) {
                                    if (word.length > 3) { // Skip very short words
                                        val sentMessageNode = accessibilityService.findNodeByText(word)
                                        if (sentMessageNode != null) {
                                            Log.d(TAG, "Found our sent message in chat, confirming success")
                                            return true
                                        }
                                    }
                                }
                                
                                // If we couldn't verify but got this far, assume success
                                Log.d(TAG, "Message likely sent successfully")
                                return true
                            } else {
                                Log.e(TAG, "Could not find send button in Telegram")
                            }
                        } else {
                            Log.e(TAG, "Could not find message input field in Telegram")
                        }
                    } else {
                        Log.e(TAG, "Could not find contact: $contactOrPhone in Telegram")
                        Toast.makeText(context, "Could not find contact: $contactOrPhone in Telegram", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "Could not find search input field in Telegram")
                }
            } else {
                Log.e(TAG, "Could not find search button in Telegram")
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Telegram message: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
} 