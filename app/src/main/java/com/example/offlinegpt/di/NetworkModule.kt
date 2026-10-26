package com.example.offlinegpt.di

import com.example.offlinegpt.data.TavilySearchRequest
import com.example.offlinegpt.data.engine.TavilySearchResponse
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Named
import jakarta.inject.Singleton
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import kotlin.jvm.java

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    @Named("TavilyApiKey")
    fun provideTavilyApiKey(): String {
        // Ideally fetch this from BuildConfig.TAVILY_API_KEY
        return "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"
    }

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.tavily.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideTavilyApiService(retrofit: Retrofit): TavilyApiService {
        return retrofit.create(TavilyApiService::class.java)
    }
}

interface TavilyApiService {
    @POST("search")
    suspend fun search(
        @Header("Authorization") apiKey: String, // Format: "Bearer tvly-..."
        @Body request: TavilySearchRequest
    ): Response<TavilySearchResponse>

    companion object {
        const val BASE_URL = "https://api.tavily.com/"
    }
}