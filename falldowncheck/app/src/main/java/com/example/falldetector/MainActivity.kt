package com.example.falldetector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.falldetector.ui.MainScreen
import com.example.falldetector.ui.theme.FallDetectorTheme

/**
 * 入口 Activity — 申请权限 + 展示 Compose UI
 */
class MainActivity : ComponentActivity() {

    private var hasNotificationPermission = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // API < 33 不需要运行时权限
        }

        setContent {
            FallDetectorTheme {
                MainScreen(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    hasNotificationPermission = hasNotificationPermission
                )
            }
        }
    }
}
