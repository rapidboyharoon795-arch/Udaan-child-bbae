package com.udaan.child

import android.telephony.TelephonyManager // <--- IMPORT YAHAAN ADD KARO
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.udaan.child.CallRecorderService // <--- IMPORT YAHAAN ADD KARO

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == TelephonyManager.ACTION_PHONE_STATE) { // Corrected here
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                
                val serviceIntent = Intent(context, CallRecorderService::class.java)
                serviceIntent.action = "START_RECORDING"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
