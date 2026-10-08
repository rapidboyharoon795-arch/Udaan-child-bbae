package com.udaan.child

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // Dynamic permission list according to Android version
    private val requiredPermissions: Array<String> by lazy {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )
        // POST_NOTIFICATIONS Android 13+ (API 33) me required hai
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.toTypedArray()
    }

    private lateinit var statusText: TextView
    private lateinit var permissionButton: Button
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        permissionButton = findViewById(R.id.permissionButton)
        startButton = findViewById(R.id.startButton)

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun setupListeners() {
        permissionButton.setOnClickListener {
            requestAllPermissions()
        }

        startButton.setOnClickListener {
            if (hasAllPermissions()) {
                startRecordingService()
            } else {
                Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show()
                requestAllPermissions()
            }
        }
    }

    private fun startRecordingService() {
        val serviceIntent = Intent(this, CallRecorderService::class.java).apply {
            putExtra("ACTION", "START_SERVICE")
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        statusText.text = "Status: Service Running"
    }

    private fun requestAllPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        } else {
            Toast.makeText(this, "All permissions already granted", Toast.LENGTH_SHORT).show()
            updateUIState()
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updateUIState() {
        if (hasAllPermissions()) {
            statusText.text = "Status: Permissions Granted (Ready)"
        } else {
            statusText.text = "Status: Permissions Required"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val denied = mutableListOf<String>()
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    denied.add(permissions[i])
                }
            }

            if (denied.isEmpty()) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
                startRecordingService()
            } else {
                showOpenSettingsDialog(denied)
            }
        }
    }

    private fun showOpenSettingsDialog(denied: List<String>) {
        val cleanNames = denied.map { it.substringAfterLast(".") }
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("App requires these permissions to function:\n\n• " + cleanNames.joinToString("\n• "))
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
