package com.madhusudhan.biosphereai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.madhusudhan.biosphereai.ui.BioSphereApp
import com.madhusudhan.biosphereai.ui.theme.BioSphereAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BioSphereAITheme {
                BioSphereApp()
            }
        }
    }
}
