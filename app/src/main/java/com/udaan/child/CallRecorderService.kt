package com.udaan.child

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

class CallRecorderService : Service() {

    companion object {
        private const val TAG = "CallRecorderService"
        private const val CHANNEL_ID = "udaan_recorder_channel"
        private const val NOTIFICATION_ID = 101
    }

    private var telephonyManager: TelephonyManager? = null
    private var phoneListener: PhoneStateListener? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var lastPhoneNumber = ""
    private var lastCallType = "UNKNOWN"
    private var callStartTime = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        registerPhoneListener()
        // Resume queue processing on start
        UploadManager.processQueue(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UdaanChild Active")
            .setContentText("Call monitoring running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return

        phoneListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        lastPhoneNumber = phoneNumber ?: "unknown"
                        lastCallType = "INCOMING"
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        val number = phoneNumber ?: lastPhoneNumber.ifEmpty { "unknown" }
                        lastPhoneNumber = number
                        if (!isRecording) {
                            if (lastCallType != "INCOMING") {
                                lastCallType = "OUTGOING"
                            }
                            startRecording(number)
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (isRecording) {
                            stopRecording()
                        }
                        lastCallType = "UNKNOWN"
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        phoneListener?.let {
            telephonyManager?.listen(it, PhoneStateListener.LISTEN_CALL_STATE)
        }
        Log.d(TAG, "Phone listener registered")
    }

    private fun startRecording(phone: String) {
        try {
            callStartTime = System.currentTimeMillis() // Pehle set karein taaki filename aur duration theek rahe
            val dir = File(filesDir, "recordings")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "call_${phone}_${callStartTime}.mp4")

            @Suppress("DEPRECATION")
            val recorder: MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            Log.d(TAG, "Recording started: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "startRecording error", e)
            isRecording = false
        }
    }

    private fun stopRecording() {
        try {
            val recorder = mediaRecorder ?: return
            val duration = (System.currentTimeMillis() - callStartTime) / 1000

            recorder.stop()
            recorder.release()
            mediaRecorder = null
            isRecording = false

            val dir = File(filesDir, "recordings")
            val latest = dir.listFiles()?.maxByOrNull { it.lastModified() }
            val phone = lastPhoneNumber

            if (latest != null) {
                Log.d(TAG, "Recording stopped: ${latest.name}, dur=${duration}s")
                UploadManager.uploadRecording(
                    context = applicationContext,
                    file = latest,
                    phoneNumber = phone,
                    callType = lastCallType,
                    duration = duration,
                    timestamp = callStartTime
                )
            } else {
                Log.e(TAG, "stopRecording: no file found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording error", e)
            isRecording = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recorder",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "udaan:recorder").apply {
            acquire(10 * 60 * 1000L) // 10 min safe timeout
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        if (isRecording) {
            try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (_: Exception) {}
            isRecording = false
        }
        phoneListener?.let {
            telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        phoneListener = null
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
