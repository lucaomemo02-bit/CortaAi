package com.cortaai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF00E5D1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = Color.Black,
                    background = Color(0xFF0B0B0D),
                    surface = Color(0xFF16161A),
                    onSurface = Color.White,
                    onBackground = Color.White,
                    surfaceVariant = Color(0xFF26262C),
                )
            ) {
                EditorScreen()
            }
        }
    }
}
