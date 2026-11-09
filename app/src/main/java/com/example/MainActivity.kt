package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.reminder.NotificationHelper
import com.example.ui.navigation.MailMindApp
import com.example.ui.theme.MailMindTheme
import com.example.viewmodel.MailMindViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MailMindViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Android notification channel for proactive deadline alerts
        NotificationHelper.initNotificationChannel(this)
        com.example.auth.GoogleAuthManager.ensureFirebaseInitialized(this)

        setContent {
            // Request runtime permissions gracefully: Audio recording for voice assistant & Notifications
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                // Permissions handled gracefully in UI
            }

            LaunchedEffect(Unit) {
                val permissionsToRequest = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (permissionsToRequest.isNotEmpty()) {
                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                }
                // Automatically greet on startup and activate 20-second inactivity listening session
                kotlinx.coroutines.delay(1200)
                viewModel.greetOnStartup()
            }

            MailMindTheme {
                MailMindApp(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
