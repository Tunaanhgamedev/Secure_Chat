package com.example.securechat

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SecureChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
