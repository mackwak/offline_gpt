package com.example.offlinegpt

import android.app.Application
import com.google.firebase.FirebaseApp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

open class OfflineGPTBaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        PDFBoxResourceLoader.init(this)
    }
}
