package com.example.offlinegpt

import android.app.Application
import com.google.firebase.FirebaseApp

open class OfflineGPTBaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
