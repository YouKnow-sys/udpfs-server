package com.udpfs.app

import android.app.Application
import com.udpfs.app.core.ServerRepository

class UdpfsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServerRepository.init(this)
    }
}
