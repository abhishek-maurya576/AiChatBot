package com.org.aichatbot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.org.aichatbot.MainActivity
import com.org.aichatbot.R

/**
 * A service that displays a floating "eye" widget to monitor accessibility operations
 */
class FloatingEyeService : Service() {
    private val TAG = "FloatingEyeService"
    
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "FloatingEyeChannel"
    
    // Window manager and view references
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var expandedView: CardView
    private lateinit var eyeImageView: ImageView
    private lateinit var statusTextView: TextView
    private lateinit var operationTextView: TextView
    private lateinit var detailsTextView: TextView
    
    // Parameters for the floating view
    private val params by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
    }
    
    // State management
    private var isExpanded = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    // Status flow for operations
    companion object {
        private val _currentOperation = MutableStateFlow<OperationInfo?>(null)
        val currentOperation = _currentOperation.asStateFlow()
        
        // Update operation from outside the service
        fun updateOperation(operation: String, details: String = "") {
            _currentOperation.value = OperationInfo(operation, details, System.currentTimeMillis())
        }
        
        // Clear operation
        fun clearOperation() {
            _currentOperation.value = null
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupFloatingView()
        startMonitoring()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Eye Monitor Service"
            val descriptionText = "Shows operations performed by the AI Agent"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Agent Monitor")
            .setContentText("Monitoring operations in progress")
            .setSmallIcon(R.drawable.ic_eye)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun setupFloatingView() {
        try {
            // Inflate the layout
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_eye_layout, null)
            
            // Get references to views
            eyeImageView = floatingView.findViewById(R.id.eyeImageView)
            expandedView = floatingView.findViewById(R.id.expandedView)
            statusTextView = floatingView.findViewById(R.id.statusTextView)
            operationTextView = floatingView.findViewById(R.id.operationTextView)
            detailsTextView = floatingView.findViewById(R.id.detailsTextView)
            
            // Initially collapsed
            expandedView.visibility = View.GONE
            
            // Setup touch listener for dragging
            eyeImageView.setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    when (event?.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (Math.abs(event.rawX - initialTouchX) < 10 && 
                                Math.abs(event.rawY - initialTouchY) < 10) {
                                toggleExpandCollapse()
                            }
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(floatingView, params)
                            return true
                        }
                        else -> return false
                    }
                }
            })
            
            // Add the view to window manager
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up floating view: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Failed to initialize monitoring view", Toast.LENGTH_SHORT).show()
            stopSelf() // Stop the service if we can't set up the view
        }
    }
    
    private fun toggleExpandCollapse() {
        isExpanded = !isExpanded
        expandedView.visibility = if (isExpanded) View.VISIBLE else View.GONE
    }
    
    private fun startMonitoring() {
        // Only start monitoring if the views are initialized
        if (!::eyeImageView.isInitialized || !::statusTextView.isInitialized || 
            !::operationTextView.isInitialized || !::detailsTextView.isInitialized) {
            Log.e(TAG, "Cannot start monitoring - views not initialized")
            stopSelf()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                try {
                    // Update UI based on current operation
                    val operation = _currentOperation.value
                    if (operation != null) {
                        val timeSinceOperation = System.currentTimeMillis() - operation.timestamp
                        
                        // Blink the eye when active
                        if (timeSinceOperation < 3000) {
                            eyeImageView.alpha = if (eyeImageView.alpha > 0.6f) 0.6f else 1.0f
                            statusTextView.text = "Active"
                        } else {
                            eyeImageView.alpha = 1.0f
                            statusTextView.text = "Idle"
                        }
                        
                        // Update text views
                        operationTextView.text = operation.operation
                        detailsTextView.text = operation.details
                    } else {
                        eyeImageView.alpha = 1.0f
                        statusTextView.text = "Idle"
                        operationTextView.text = "No current operation"
                        detailsTextView.text = ""
                    }
                    
                    handler.postDelayed(this, 500) // Update every half second
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring runnable: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        
        handler.post(runnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle intent commands if needed
        return START_STICKY
    }
    
    /**
     * Operation information data class
     */
    data class OperationInfo(
        val operation: String,
        val details: String,
        val timestamp: Long
    )
} 