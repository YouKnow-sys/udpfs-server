package com.udpfs.app

import com.udpfs.app.ui.theme.UdpfsTheme
import com.udpfs.app.ui.UdpfsApp
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import android.os.Bundle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UdpfsTheme {
                UdpfsApp()
            }
        }
    }
}
