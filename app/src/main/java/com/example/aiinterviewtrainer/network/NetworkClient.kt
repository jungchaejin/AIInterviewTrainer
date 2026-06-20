package com.example.aiinterviewtrainer.network

import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(GEMINI_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val geminiApiService: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }
}