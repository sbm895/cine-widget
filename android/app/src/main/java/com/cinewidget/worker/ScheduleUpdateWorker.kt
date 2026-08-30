package com.cinewidget.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cinewidget.data.api.RetrofitClient
import com.cinewidget.widget.CinemaWidget
import com.google.gson.Gson

class ScheduleUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        val customUrl = prefs.getString("backend_url", null)
        val forceRefresh = inputData.getBoolean("force_refresh", true)

        if (customUrl.isNullOrBlank()) {
            prefs.edit().putBoolean("is_syncing", false).commit()
            CinemaWidget().updateAll(context)
            return Result.failure()
        }

        return try {
            val apiService = RetrofitClient.createService(customUrl)

            // Consultar endpoint unificado con refresh según sea manual o periódico
            val response = apiService.getUnifiedSchedule(refresh = forceRefresh)
            val json = Gson().toJson(response)

            prefs.edit()
                .putString("last_schedule_json", json)
                .putBoolean("is_syncing", false)
                .commit()

            // Pre-descargar imágenes en segundo plano a la caché local sin bloquear la UI del widget
            com.cinewidget.util.WidgetImageCache.prefetchPosters(context, response)

            CinemaWidget().updateAll(context)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            prefs.edit().putBoolean("is_syncing", false).commit()
            CinemaWidget().updateAll(context)
            Result.retry()
        }
    }
}

