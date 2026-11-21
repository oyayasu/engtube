package com.oyayasu.engtube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView

/** Explicitly opt-in shortcut. This does not capture or inspect YouTube data. */
class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val open = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("open_capture", true)
        }
        val pending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "engtube_overlay")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("EngTube floating button")
            .setContentText("Tap to open EngTube; stop from Settings.")
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(821, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(821, notification)
        if (bubble == null) showBubble()
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            "engtube_overlay", "EngTube floating access",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun showBubble() {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager
        val density = resources.displayMetrics.density
        val size = (52 * density).toInt()
        val params = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - size - 20 * density).toInt()
            y = (195 * density).toInt()
        }
        val shortcut = TextView(this).apply {
            text = "EN"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(41, 35, 53))
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(216, 232, 199))
                cornerRadius = size.toFloat()
                setStroke((2 * density).toInt(), android.graphics.Color.rgb(69, 108, 66))
            }
            elevation = 8 * density
        }
        val threshold = ViewConfiguration.get(this).scaledTouchSlop
        var touchX = 0f
        var touchY = 0f
        var originX = 0
        var originY = 0
        shortcut.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchX = event.rawX
                    touchY = event.rawY
                    originX = params.x
                    originY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (originX + event.rawX - touchX).toInt()
                    params.y = (originY + event.rawY - touchY).toInt()
                    runCatching { manager.updateViewLayout(shortcut, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (kotlin.math.abs(event.rawX - touchX) < threshold &&
                        kotlin.math.abs(event.rawY - touchY) < threshold) {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("open_capture", true)
                        })
                    }
                    true
                }
                else -> false
            }
        }
        runCatching {
            manager.addView(shortcut, params)
            bubble = shortcut
        }.onFailure { stopSelf() }
    }

    override fun onDestroy() {
        bubble?.let { view -> runCatching { windowManager?.removeView(view) } }
        bubble = null
        super.onDestroy()
    }
}
