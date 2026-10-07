package com.udaan.child

import android.os.Build // <--- IMPORT YAHAAN ADD KARO
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.udaan.child.CallRecorderService // <--- IMPORT YAHAAN ADD KARO

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, CallRecorderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Build use ho raha hai
                context?.startForegroundService(serviceIntent)
            } else {
                context?.startService(serviceIntent)
            }
        }
    }
}
