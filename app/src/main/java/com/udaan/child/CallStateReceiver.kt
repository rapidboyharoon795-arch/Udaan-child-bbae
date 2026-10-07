package com.udaan.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val serviceIntent = Intent(context, CallRecorderService::class.java).apply {
            action = "ACTION_CALL_STATE_CHANGED"
            putExtra("state", state)
        }
        context.startForegroundService(serviceIntent)
        Log.d("UdaanChild", "Receiver State: $state")
    }
}
