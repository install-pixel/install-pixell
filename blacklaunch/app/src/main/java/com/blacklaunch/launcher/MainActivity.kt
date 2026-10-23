package com.blacklaunch.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.blacklaunch.launcher.ui.BlackHomeScreen
import com.blacklaunch.launcher.ui.theme.BlackLaunchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlackLaunchTheme {
                BlackHomeScreen()
            }
        }
    }
}
