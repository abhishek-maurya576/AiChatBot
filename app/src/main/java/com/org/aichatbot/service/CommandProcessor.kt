package com.org.aichatbot.service

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.IOException

/**
 * Command processor for analyzing user commands using Gemini API and executing them
 */
class CommandProcessor(
    private val context: Context,
    private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val appControlManager = AppControlManager(context)
    
    companion object {
        private const val TAG = "CommandProcessor"
        
        // Command types
        const val COMMAND_OPEN_APP = "OPEN_APP"
        const val COMMAND_OPEN_WEBSITE = "OPEN_WEBSITE"
        const val COMMAND_SEARCH = "SEARCH"
        const val COMMAND_SEND_MESSAGE = "SEND_MESSAGE"
        const val COMMAND_CALL = "MAKE_CALL"
        const val COMMAND_TOGGLE_WIFI = "TOGGLE_WIFI"
        const val COMMAND_TOGGLE_BLUETOOTH = "TOGGLE_BLUETOOTH"
        const val COMMAND_UNKNOWN = "UNKNOWN"

        // Message platforms
        const val PLATFORM_WHATSAPP = "whatsapp"
        const val PLATFORM_SMS = "sms"
        const val PLATFORM_TELEGRAM = "telegram"
        const val PLATFORM_DEFAULT = PLATFORM_WHATSAPP
    }
    
    // Status flow
    private val _processingStatus = MutableStateFlow<ProcessingStatus>(ProcessingStatus.Idle)
    val processingStatus: StateFlow<ProcessingStatus> = _processingStatus.asStateFlow()
    
    /**
     * Process a command from the user
     * @param userInput The user's command text
     */
    suspend fun processCommand(userInput: String): CommandResult {
        _processingStatus.value = ProcessingStatus.Processing
        
        // First, try to analyze the command with Gemini API
        val commandInfo = analyzeCommandWithGemini(userInput)
        
        // If Gemini API fails, fall back to basic analysis
        val result = if (commandInfo != null) {
            executeCommand(commandInfo)
        } else {
            fallbackCommandExecution(userInput)
        }
        
        _processingStatus.value = ProcessingStatus.Idle
        return result
    }
    
    /**
     * Execute a command based on the analyzed command info
     */
    private fun executeCommand(commandInfo: CommandInfo): CommandResult {
        Log.d(TAG, "Executing command: ${commandInfo.commandType}")
        
        return when (commandInfo.commandType) {
            COMMAND_OPEN_APP -> {
                val appName = commandInfo.parameters["app_name"] ?: ""
                val success = appControlManager.openApp(appName)
                CommandResult(
                    wasExecuted = success,
                    commandType = COMMAND_OPEN_APP,
                    message = if (success) "Opened $appName successfully" 
                            else "Could not find or open $appName. The app might not be installed."
                )
            }
            COMMAND_OPEN_WEBSITE -> {
                val url = commandInfo.parameters["url"] ?: ""
                val success = appControlManager.openWebsite(url)
                CommandResult(
                    wasExecuted = success,
                    commandType = COMMAND_OPEN_WEBSITE,
                    message = if (success) "Opened website $url successfully" 
                            else "Could not open website $url."
                )
            }
            COMMAND_SEARCH -> {
                val query = commandInfo.parameters["query"] ?: ""
                val searchEngine = commandInfo.parameters["search_engine"] ?: "google"
                
                val success = when (searchEngine.lowercase()) {
                    "youtube" -> appControlManager.openYouTubeSearch(query)
                    else -> appControlManager.openGoogleSearch(query)
                }
                
                CommandResult(
                    wasExecuted = success,
                    commandType = COMMAND_SEARCH,
                    message = if (success) "Searched for '$query' on ${searchEngine.capitalize()}" 
                            else "Could not perform search for '$query'."
                )
            }
            COMMAND_SEND_MESSAGE -> {
                val phoneNumber = commandInfo.parameters["phone_number"] ?: ""
                val message = commandInfo.parameters["message"] ?: ""
                val contactName = commandInfo.parameters["contact_name"] ?: ""
                val platform = commandInfo.parameters["platform"]?.lowercase() ?: PLATFORM_DEFAULT
                
                // If no accessibility service, prompt the user
                if (AppControlAccessibilityService.getInstance() == null) {
                    return CommandResult(
                        wasExecuted = false,
                        commandType = COMMAND_SEND_MESSAGE,
                        message = "To send messages automatically, please enable the accessibility service in settings. Would you like me to open accessibility settings for you?"
                    )
                }
                
                // Prefer contact name over phone number if both are present
                val recipient = if (contactName.isNotEmpty()) contactName else phoneNumber
                
                if (recipient.isEmpty()) {
                    return CommandResult(
                        wasExecuted = false,
                        commandType = COMMAND_SEND_MESSAGE,
                        message = "I couldn't determine who to send the message to. Please specify a contact name or phone number clearly."
                    )
                }
                
                if (message.isEmpty()) {
                    return CommandResult(
                        wasExecuted = false,
                        commandType = COMMAND_SEND_MESSAGE,
                        message = "I couldn't find a message to send. Please specify what message you'd like to send."
                    )
                }
                
                // Log the attempt for debugging
                Log.d(TAG, "Attempting to send message via $platform to $recipient: '$message'")
                
                // Send message using the appropriate platform
                val success = when (platform) {
                    PLATFORM_WHATSAPP -> appControlManager.sendWhatsAppMessage(recipient, message)
                    PLATFORM_SMS -> appControlManager.sendSmsMessage(recipient, message) 
                    PLATFORM_TELEGRAM -> {
                        Log.d(TAG, "Starting Telegram message process to: $recipient")
                        // Add a small delay before starting Telegram process to ensure UI is ready
                        Thread.sleep(500)
                        appControlManager.sendTelegramMessage(recipient, message)
                    }
                    else -> appControlManager.sendWhatsAppMessage(recipient, message) // Default to WhatsApp
                }
                
                val platformName = when (platform) {
                    PLATFORM_WHATSAPP -> "WhatsApp"
                    PLATFORM_SMS -> "SMS"
                    PLATFORM_TELEGRAM -> "Telegram"
                    else -> "WhatsApp"
                }
                
                return if (success) {
                    Log.d(TAG, "Successfully sent message via $platformName to $recipient")
                    CommandResult(
                        wasExecuted = true,
                        commandType = COMMAND_SEND_MESSAGE,
                        message = "Message sent successfully to $recipient via $platformName"
                    )
                } else {
                    Log.e(TAG, "Failed to send message via $platformName to $recipient")
                    // Provide more detailed error feedback based on platform
                    val errorMessage = when (platform) {
                        PLATFORM_WHATSAPP -> "Could not send WhatsApp message to $recipient. Please check that WhatsApp is installed and the contact exists in your WhatsApp contacts."
                        PLATFORM_SMS -> "Could not send SMS to $recipient. Please check that the default messaging app is accessible and the contact exists."
                        PLATFORM_TELEGRAM -> "Could not send Telegram message to $recipient. Please verify that Telegram is installed and you have this contact in your Telegram contacts."
                        else -> "Could not send message to $recipient. Please check that the messaging app is installed and the contact exists."
                    }
                    
                    CommandResult(
                        wasExecuted = false,
                        commandType = COMMAND_SEND_MESSAGE,
                        message = errorMessage
                    )
                }
            }
            COMMAND_CALL -> {
                val phoneNumber = commandInfo.parameters["phone_number"] ?: ""
                val contactName = commandInfo.parameters["contact_name"] ?: ""
                
                val success = if (phoneNumber.isNotEmpty()) {
                    appControlManager.makePhoneCall(phoneNumber)
                } else if (contactName.isNotEmpty()) {
                    // For now, we don't have contact lookup implemented
                    appControlManager.makePhoneCall(contactName)
                } else {
                    false
                }
                
                CommandResult(
                    wasExecuted = success,
                    commandType = COMMAND_CALL,
                    message = if (success) "Call initiated successfully" 
                            else "Could not make the call. Please check the phone number or contact name."
                )
            }
            COMMAND_TOGGLE_WIFI -> {
                val enable = commandInfo.parameters["enable"]?.toBoolean() ?: true
                val success = appControlManager.toggleWiFi(enable)
                
                CommandResult(
                    wasExecuted = success,
                    commandType = COMMAND_TOGGLE_WIFI,
                    message = if (success) "Wi-Fi ${if (enable) "enabled" else "disabled"} successfully" 
                            else "Could not control Wi-Fi. On newer Android versions, you need to use the settings panel."
                )
            }
            COMMAND_TOGGLE_BLUETOOTH -> {
                val enable = commandInfo.parameters["enable"]?.toBoolean() ?: true
                val success = appControlManager.toggleBluetooth(enable)
                
                CommandResult(
                    wasExecuted = success,
                    commandType = COMMAND_TOGGLE_BLUETOOTH,
                    message = if (success) "Bluetooth ${if (enable) "enabled" else "disabled"} successfully" 
                            else "Could not control Bluetooth. On newer Android versions, you need to use the settings panel."
                )
            }
            else -> CommandResult(
                wasExecuted = false,
                commandType = COMMAND_UNKNOWN,
                message = "Unknown command type."
            )
        }
    }
    
    /**
     * Fallback command execution using basic string matching
     */
    private fun fallbackCommandExecution(userInput: String): CommandResult {
        val input = userInput.lowercase()
        
        // Check for app opening commands
        if (input.contains("open")) {
            val appNames = listOf("whatsapp", "youtube", "chrome", "gmail", "maps", "facebook", 
                "twitter", "instagram", "settings", "camera", "calculator", "clock", "calendar", 
                "photos", "play store", "spotify", "netflix")
            
            for (app in appNames) {
                if (input.contains(app)) {
                    val success = appControlManager.openApp(app)
                    return CommandResult(
                        wasExecuted = success,
                        commandType = COMMAND_OPEN_APP,
                        message = if (success) "Opened $app" else "Failed to open $app"
                    )
                }
            }
        }
        
        // Check for website opening
        if (input.contains("go to") || input.contains("visit") || (input.contains("open") && 
            (input.contains(".com") || input.contains(".org") || input.contains(".net")))) {
            
            // Extract URL - very basic implementation
            val words = input.split(" ")
            for (word in words) {
                if (word.contains(".")) {
                    val success = appControlManager.openWebsite(word)
                    return CommandResult(
                        wasExecuted = success,
                        commandType = COMMAND_OPEN_WEBSITE,
                        message = if (success) "Opened website $word" else "Failed to open website"
                    )
                }
            }
        }
        
        // Check for searches
        if (input.contains("search")) {
            val searchPhrase = if (input.contains("search for")) {
                input.substringAfter("search for")
            } else {
                input.substringAfter("search")
            }
            
            val searchEngine = when {
                input.contains("youtube") -> "youtube"
                else -> "google"
            }
            
            val success = if (searchEngine == "youtube") {
                appControlManager.openYouTubeSearch(searchPhrase.trim())
            } else {
                appControlManager.openGoogleSearch(searchPhrase.trim())
            }
            
            return CommandResult(
                wasExecuted = success,
                commandType = COMMAND_SEARCH,
                message = if (success) "Searched for '$searchPhrase' on $searchEngine" 
                          else "Failed to search"
            )
        }
        
        // Check for Wi-Fi commands
        if (input.contains("wifi") || input.contains("wi-fi") || input.contains("wi fi")) {
            val enable = !input.contains("off") && !input.contains("disable")
            val success = appControlManager.toggleWiFi(enable)
            
            return CommandResult(
                wasExecuted = success,
                commandType = COMMAND_TOGGLE_WIFI,
                message = if (success) "Wi-Fi ${if (enable) "enabled" else "disabled"}" 
                          else "Failed to toggle Wi-Fi"
            )
        }
        
        // Check for Bluetooth commands
        if (input.contains("bluetooth")) {
            val enable = !input.contains("off") && !input.contains("disable")
            val success = appControlManager.toggleBluetooth(enable)
            
            return CommandResult(
                wasExecuted = success,
                commandType = COMMAND_TOGGLE_BLUETOOTH,
                message = if (success) "Bluetooth ${if (enable) "enabled" else "disabled"}" 
                          else "Failed to toggle Bluetooth"
            )
        }
        
        // Check for messaging commands across platforms
        if (input.contains("send") || input.contains("message") || input.contains("text") || 
            input.contains("tell") || input.contains("whatsapp") || input.contains("sms") || 
            input.contains("telegram")) {
            
            // Try to determine the platform
            var platform = PLATFORM_DEFAULT
            
            if (input.contains("text") || input.contains("sms")) {
                platform = PLATFORM_SMS
            } else if (input.contains("telegram")) {
                platform = PLATFORM_TELEGRAM
            } else if (input.contains("whatsapp")) {
                platform = PLATFORM_WHATSAPP
            }
            
            // Try to extract the contact name and message
            var contactOrPhone = ""
            var message = ""
            
            // Check for phone number pattern in the input
            val phoneNumberPattern = Regex("\\b[0-9]{10}\\b|\\+[0-9]{12,13}\\b")
            val phoneNumberMatch = phoneNumberPattern.find(input)
            
            if (phoneNumberMatch != null) {
                // Found a phone number
                contactOrPhone = phoneNumberMatch.value
                
                // Extract the message part
                val startIdx = input.indexOf(contactOrPhone) + contactOrPhone.length
                if (startIdx < input.length) {
                    message = input.substring(startIdx).trim()
                    
                    // Clean up the message - remove phrases like "the message is", "with text", etc.
                    val cleanupPhrases = listOf(" the message is ", " with text ", " saying ", " that says ")
                    for (phrase in cleanupPhrases) {
                        if (message.contains(phrase, ignoreCase = true)) {
                            message = message.substringAfter(phrase, message)
                        }
                    }
                }
                
                if (message.isEmpty()) {
                    message = "Hello"
                }
                
                Log.d(TAG, "Found phone number: $contactOrPhone with message: $message")
                
                // Send the message using the identified platform
                val success = when (platform) {
                    PLATFORM_WHATSAPP -> appControlManager.sendWhatsAppMessage(contactOrPhone, message)
                    PLATFORM_SMS -> appControlManager.sendSmsMessage(contactOrPhone, message)
                    PLATFORM_TELEGRAM -> appControlManager.sendTelegramMessage(contactOrPhone, message)
                    else -> appControlManager.sendWhatsAppMessage(contactOrPhone, message)
                }
                
                val platformName = when (platform) {
                    PLATFORM_WHATSAPP -> "WhatsApp"
                    PLATFORM_SMS -> "SMS"
                    PLATFORM_TELEGRAM -> "Telegram"
                    else -> "WhatsApp"
                }
                
                return CommandResult(
                    wasExecuted = success,
                    commandType = COMMAND_SEND_MESSAGE,
                    message = if (success) "Message sent to $contactOrPhone via $platformName: \"$message\"" 
                            else "Failed to send message to $contactOrPhone via $platformName"
                )
            }
            
            // Common patterns in messaging commands
            if (input.contains("to ")) {
                // Pattern: "send message to [contact] [message]" or "send [message] to [contact]"
                val toIndex = input.indexOf("to ")
                
                if (toIndex > 0) {
                    val afterTo = input.substring(toIndex + 3).trim()
                    
                    // Try to find the contact name
                    // Look for transition words that might indicate the end of the contact name
                    val transitionWords = listOf(" that ", " saying ", ": ", " with ", " message ", " tell ", " the message ", " is ")
                    var messageStartIndex = -1
                    var transitionWord = ""
                    
                    for (word in transitionWords) {
                        if (afterTo.contains(word, ignoreCase = true)) {
                            messageStartIndex = afterTo.indexOf(word, ignoreCase = true)
                            transitionWord = word
                            break
                        }
                    }
                    
                    if (messageStartIndex >= 0) {
                        // We found a transition word, so extract contact and message
                        contactOrPhone = afterTo.substring(0, messageStartIndex).trim()
                        message = afterTo.substring(messageStartIndex + transitionWord.length).trim()
                    } else {
                        // No clear transition, try to identify if the text after "to" is a contact or message
                        // If it contains digits, it might be a phone number
                        val potentialContact = afterTo.trim()
                        if (potentialContact.any { it.isDigit() }) {
                            contactOrPhone = potentialContact
                            // Check if there's a message before "to"
                            val beforeTo = input.substring(0, toIndex).trim()
                            val possibleMessageStarts = listOf("send ", "message ", "saying ", "that says ", "text ")
                            
                            for (start in possibleMessageStarts) {
                                if (beforeTo.contains(start, ignoreCase = true)) {
                                    val startIndex = beforeTo.indexOf(start, ignoreCase = true) + start.length
                                    message = beforeTo.substring(startIndex).trim()
                                    break
                                }
                            }
                        } else {
                            // It's probably a contact name
                            contactOrPhone = potentialContact
                            // See if there's anything before "to" that could be the message
                            val beforeTo = input.substring(0, toIndex).trim()
                            val possibleMessageStarts = listOf("send ", "message ", "saying ", "that says ", "text ")
                            
                            for (start in possibleMessageStarts) {
                                if (beforeTo.contains(start, ignoreCase = true)) {
                                    val startIndex = beforeTo.indexOf(start, ignoreCase = true) + start.length
                                    message = beforeTo.substring(startIndex).trim()
                                    break
                                }
                            }
                        }
                    }
                }
                
                // Look for platform indicators within the command
                if (input.contains(" on whatsapp") || input.contains(" via whatsapp")) {
                    platform = PLATFORM_WHATSAPP
                } else if (input.contains(" on telegram") || input.contains(" via telegram")) {
                    platform = PLATFORM_TELEGRAM
                } else if (input.contains(" by sms") || input.contains(" via sms") || input.contains(" by text") || input.contains(" via text")) {
                    platform = PLATFORM_SMS
                }
            }
            
            // If we couldn't extract contact and message with the above pattern, try a simpler approach
            if (contactOrPhone.isEmpty() || message.isEmpty()) {
                val words = input.split(" ")
                if (words.size >= 3) {
                    // Try to find common contact indicators
                    for (i in 0 until words.size - 2) {
                        if (words[i].equals("to", ignoreCase = true) || words[i].equals("for", ignoreCase = true)) {
                            // Build contact name from the next 1-3 words
                            val potentialContact = words.subList(i + 1, minOf(i + 4, words.size)).joinToString(" ")
                            if (potentialContact.isNotEmpty()) {
                                contactOrPhone = potentialContact
                                
                                // Assume the rest is the message, or if nothing else is left, use a default
                                if (i + 4 < words.size) {
                                    message = words.subList(i + 4, words.size).joinToString(" ")
                                }
                                break
                            }
                        }
                    }
                }
            }
            
            // If we still don't have a contact or message, use defaults
            if (contactOrPhone.isEmpty()) {
                return CommandResult(
                    wasExecuted = false,
                    commandType = COMMAND_SEND_MESSAGE,
                    message = "I couldn't identify a contact or phone number to send a message to. Please specify a contact name or phone number."
                )
            }
            
            if (message.isEmpty()) {
                message = "Hello"
            }
            
            // Clean up the message to remove extra phrases
            val cleanupPhrases = listOf("the message is ", "with text ", "saying ", "that says ")
            for (phrase in cleanupPhrases) {
                if (message.contains(phrase, ignoreCase = true)) {
                    message = message.replace(phrase, "", ignoreCase = true)
                }
            }
            
            Log.d(TAG, "Sending message to $contactOrPhone via $platform: $message")
            
            // Send the message using the identified platform
            val success = when (platform) {
                PLATFORM_WHATSAPP -> appControlManager.sendWhatsAppMessage(contactOrPhone, message)
                PLATFORM_SMS -> appControlManager.sendSmsMessage(contactOrPhone, message)
                PLATFORM_TELEGRAM -> appControlManager.sendTelegramMessage(contactOrPhone, message)
                else -> appControlManager.sendWhatsAppMessage(contactOrPhone, message)
            }
            
            val platformName = when (platform) {
                PLATFORM_WHATSAPP -> "WhatsApp"
                PLATFORM_SMS -> "SMS"
                PLATFORM_TELEGRAM -> "Telegram"
                else -> "WhatsApp"
            }
            
            return CommandResult(
                wasExecuted = success,
                commandType = COMMAND_SEND_MESSAGE,
                message = if (success) "Message sent to $contactOrPhone via $platformName: \"$message\"" 
                          else "Failed to send message to $contactOrPhone via $platformName"
            )
        }
        
        // If no command matched
        return CommandResult(
            wasExecuted = false,
            commandType = COMMAND_UNKNOWN,
            message = "I couldn't understand that command. Try something like 'open WhatsApp' or 'search for Android development'"
        )
    }
    
    /**
     * Analyze a command using the Gemini API
     */
    private suspend fun analyzeCommandWithGemini(userInput: String): CommandInfo? {
        if (apiKey.isBlank()) {
            Log.e(TAG, "API key is blank, cannot analyze command")
            return null
        }
        
        return try {
            // Create the JSON request body for the Gemini API
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                // Use a prompt that will return structured data
                                val prompt = """
                                    You are an AI assistant that controls a mobile device. I need you to analyze the following user command and extract structured information so I can execute it correctly.
                                    
                                    User command: "$userInput"
                                    
                                    Return your response as a valid JSON object with the following properties:
                                    - command_type: One of ["OPEN_APP", "OPEN_WEBSITE", "SEARCH", "SEND_MESSAGE", "MAKE_CALL", "TOGGLE_WIFI", "TOGGLE_BLUETOOTH", "UNKNOWN"]
                                    - parameters: An object containing relevant parameters for the command
                                    
                                    For OPEN_APP, include "app_name" in parameters.
                                    For OPEN_WEBSITE, include "url" in parameters.
                                    For SEARCH, include "query" and "search_engine" (google or youtube) in parameters.
                                    For SEND_MESSAGE, include:
                                      - "contact_name" or "phone_number" (extract exactly as specified)
                                      - "message" (the text to send)
                                      - "platform" (detect which platform to use: "whatsapp", "telegram", "sms", etc.)
                                      If no platform is specified, assume "whatsapp" as default.
                                    For MAKE_CALL, include "contact_name" or "phone_number" in parameters.
                                    For TOGGLE_WIFI and TOGGLE_BLUETOOTH, include "enable" (true/false) in parameters.
                                    
                                    Be extremely precise in identifying the user's intent:
                                    
                                    1. For messaging commands, look for keywords like:
                                       - "text", "sms", "message" for SMS
                                       - "telegram", "tg" for Telegram
                                       - "whatsapp", "wa" for WhatsApp
                                    
                                    2. Pay careful attention to the exact contact name or phone number - extract it exactly as specified. The contact name should include full names (first and last) if provided.
                                    
                                    3. The message content should be everything after contact identification phrases like "saying", "that", "with message", etc.
                                    
                                    Examples:
                                    - "text John saying I'll be late" → SEND_MESSAGE with platform="sms", contact_name="John", message="I'll be late"
                                    - "message my mom on WhatsApp that dinner is ready" → SEND_MESSAGE with platform="whatsapp", contact_name="my mom", message="dinner is ready"
                                    - "send telegram message to k drama on telegram how are you" → SEND_MESSAGE with platform="telegram", contact_name="k drama", message="how are you"
                                    - "tell Bob I'll call him back" → SEND_MESSAGE with platform="whatsapp", contact_name="Bob", message="I'll call him back"
                                    
                                    Return ONLY the JSON without any additional text or explanation.
                                """.trimIndent()
                                put("text", prompt)
                            })
                        })
                    })
                })
                
                // Add generation configuration for structured response
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)  // Lower temperature for more deterministic response
                    put("topK", 1)
                    put("topP", 0.95)
                    put("maxOutputTokens", 1000)
                })
            }.toString()
            
            // Create the request
            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()
            
            // Make the API call
            val response = suspendedRequest(request)
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "API Response: $responseBody")
                
                // Parse the response JSON to extract the generated text
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.getJSONArray("candidates")
                
                if (candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    
                    if (parts.length() > 0) {
                        val text = parts.getJSONObject(0).getString("text")
                        parseCommandInfoFromJson(text)
                    } else {
                        Log.e(TAG, "No parts in Gemini response")
                        null
                    }
                } else {
                    Log.e(TAG, "No candidates in Gemini response")
                    null
                }
            } else {
                val errorResponse = response.body?.string() ?: ""
                Log.e(TAG, "API Error Response: $errorResponse")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing command with Gemini: ${e.message}")
            null
        }
    }
    
    /**
     * Parse the JSON response from Gemini into a CommandInfo object
     */
    private fun parseCommandInfoFromJson(jsonText: String): CommandInfo? {
        return try {
            // Extract the JSON part - sometimes the model returns additional text
            val jsonRegex = """\{.*\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonRegex.find(jsonText)
            val json = JSONObject(jsonMatch?.value ?: jsonText)
            
            val commandType = json.getString("command_type")
            val params = mutableMapOf<String, String>()
            
            if (json.has("parameters")) {
                val parameters = json.getJSONObject("parameters")
                val keys = parameters.keys()
                
                while (keys.hasNext()) {
                    val key = keys.next()
                    params[key] = parameters.getString(key)
                }
            }
            
            CommandInfo(commandType, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing command JSON: ${e.message}")
            null
        }
    }
    
    /**
     * Make a suspended HTTP request for coroutines
     */
    private suspend fun suspendedRequest(request: Request): Response {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(e))
                    }
                }
                
                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(response))
                    }
                }
            })
            
            continuation.invokeOnCancellation {
                // Close any resources if needed
            }
        }
    }
    
    /**
     * Factory for creating CommandProcessor in ViewModels
     */
    class Factory(private val context: Context, private val apiKey: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CommandProcessor(context, apiKey) as T
        }
    }
}

/**
 * Data class for command processing status
 */
sealed class ProcessingStatus {
    object Idle : ProcessingStatus()
    object Processing : ProcessingStatus()
}

/**
 * Data class for command info
 */
data class CommandInfo(
    val commandType: String,
    val parameters: Map<String, String>
)

/**
 * Data class for command execution result
 */
data class CommandResult(
    val wasExecuted: Boolean,
    val commandType: String,
    val message: String
) 