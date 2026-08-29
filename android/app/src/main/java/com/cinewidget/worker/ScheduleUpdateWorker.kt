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
        return try {
            val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
            val customUrl = prefs.getString("backend_url", null)
            val apiService = if (!customUrl.isNullOrBlank()) {
                RetrofitClient.createService(customUrl)
            } else {
                RetrofitClient.apiService
            }

            val response = apiService.getUnifiedSchedule()
            val json = Gson().toJson(response)
            prefs.edit().putString("last_schedule_json", json).apply()

            CinemaWidget().updateAll(context)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
