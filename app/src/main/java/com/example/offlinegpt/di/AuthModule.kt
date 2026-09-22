package com.example.offlinegpt.di

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    private fun isLocalFlavor(packageName: String): Boolean {
        return packageName.endsWith(".dev") || packageName.endsWith(".qa")
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(@ApplicationContext context: Context): FirebaseAuth {
        val auth = FirebaseAuth.getInstance()
        return auth
    }

    @Provides
    @Singleton
    fun provideFirebaseFunctions(@ApplicationContext context: Context): FirebaseFunctions {
        val functions = FirebaseFunctions.getInstance("us-central1")
        return functions
    }


    @Provides
    @Singleton
    fun provideCredentialManager(@ApplicationContext context: Context): CredentialManager =
        CredentialManager.create(context)

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(mapOf("APP_NAME" to "OfflineGPT"))
        remoteConfig.fetchAndActivate()
        return remoteConfig
    }
}
