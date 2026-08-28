package com.udpfs.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.udpfs.app.ui.UdpfsApp
import com.udpfs.app.ui.theme.UdpfsTheme

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
