package com.udpfs.app

import com.udpfs.app.core.ServerRepository
import android.app.Application

class UdpfsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServerRepository.init(this)
    }
}
