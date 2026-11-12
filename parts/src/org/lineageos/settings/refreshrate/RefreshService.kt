/*
 * Copyright (C) 2020 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.refreshrate

import android.app.*
import android.content.*
import android.os.*
import android.util.Log

class RefreshService : android.app.Service() {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var destroyed = false
    private var screenOn = false
    private var receiverRegistered = false
    private var listenerRegistered = false
    private var previous: String? = null
    private var retryCount = 0
    private val retry = Runnable { foreground(true) }
    private lateinit var utils: RefreshUtils
    private var tasks: IActivityTaskManager? = null
    private val refresh = Runnable { foreground(false) }
    private val listener =
        object : TaskStackListener() {
            override fun onTaskStackChanged() {
                if (!destroyed) {
                    handler.removeCallbacks(refresh)
                    handler.post(refresh)
                }
            }

            override fun onTaskFocusChanged(taskId: Int, focused: Boolean) {
                if (focused) onTaskStackChanged()
            }
        }
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (destroyed) return
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        previous = null
                        recordApply(utils.restoreDefaultRates())
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        screenOn = true
                        foreground(true)
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        utils = RefreshUtils(this)
        tasks = ActivityTaskManager.getService()
        screenOn = getSystemService(PowerManager::class.java)?.isInteractive == true
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
        try {
            tasks?.let {
                it.registerTaskStackListener(listener)
                listenerRegistered = true
            }
        } catch (error: Exception) {
            Log.w("RefreshService", "Cannot register task listener", error)
        }
        foreground(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground(true)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(retry)
        if (receiverRegistered)
            try {
                unregisterReceiver(receiver)
            } catch (error: RuntimeException) {
                Log.w("RefreshService", "Cannot unregister receiver", error)
            }
        if (listenerRegistered)
            try {
                tasks?.unregisterTaskStackListener(listener)
            } catch (error: Exception) {
                Log.w("RefreshService", "Cannot unregister task listener", error)
            }
        recordApply(utils.restoreDefaultRates())
        super.onDestroy()
    }

    private fun recordApply(applied: Boolean) {
        handler.removeCallbacks(retry)
        if (applied) {
            retryCount = 0
        } else if (!destroyed && retryCount < 3) {
            retryCount++
            handler.postDelayed(retry, 1000)
        }
    }

    private fun foreground(force: Boolean) {
        if (destroyed) return
        if (!screenOn) {
            previous = null
            recordApply(utils.restoreDefaultRates())
            return
        }
        val packageName =
            try {
                tasks?.focusedRootTaskInfo?.topActivity?.packageName ?: ""
            } catch (error: Exception) {
                Log.w("RefreshService", "Cannot query foreground", error)
                previous = null
                utils.restoreDefaultRates()
                recordApply(false)
                return
            }
        if (!force && previous == packageName) return
        val applied =
            if (packageName.isEmpty()) utils.restoreDefaultRates()
            else utils.setRefreshRate(packageName)
        previous = if (applied) packageName else null
        recordApply(applied)
    }
}
