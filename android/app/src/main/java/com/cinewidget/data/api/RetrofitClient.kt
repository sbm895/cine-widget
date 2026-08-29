package com.cinewidget.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Configura aquí la URL de tu backend desplegado en Google Cloud o local
    // Para emulador local: "http://10.0.2.2:8000/"
    // Para Cloud Run / App Engine: "https://tu-servicio-uc.a.run.app/"
    var baseUrl: String = "https://tu-servicio-en-google-cloud.a.run.app/"

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val apiService: CinemaApiService by lazy {
        createService(baseUrl)
    }

    fun createService(url: String): CinemaApiService {
        val sanitizedUrl = if (url.endsWith("/")) url else "$url/"
        return Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CinemaApiService::class.java)
    }
}
