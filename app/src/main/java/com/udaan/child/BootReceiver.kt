package com.udaan.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed event received")

            // 1. Service start with safety check
            val serviceIntent = Intent(context, CallRecorderService::class.java).apply {
                putExtra("ACTION", "START_SERVICE")
            }

            try {
                ContextCompat.startForegroundService(context, serviceIntent)
                Log.d(TAG, "CallRecorderService started successfully on boot")
            } catch (e: Exception) {
                // Android 12+ background start restriction catch
                Log.e(TAG, "Failed to start service directly from boot: ${e.message}", e)
            }

            // 2. Schedule background periodic worker
            try {
                CallLogWorker.schedule(context)
                Log.d(TAG, "CallLogWorker scheduled successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule CallLogWorker", e)
            }
        }
    }
}
