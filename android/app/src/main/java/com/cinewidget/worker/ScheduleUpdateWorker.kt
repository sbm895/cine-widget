package com.cinewidget.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cinewidget.data.api.RetrofitClient
import com.cinewidget.data.model.CinemaSchedule
import com.cinewidget.data.model.UnifiedScheduleResponse
import com.cinewidget.widget.CinemaWidget
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScheduleUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        val customUrl = prefs.getString("backend_url", null)

        if (customUrl.isNullOrBlank()) {
            prefs.edit().putBoolean("is_syncing", false).apply()
            CinemaWidget().updateAll(context)
            return Result.failure()
        }

        return try {
            val apiService = RetrofitClient.createService(customUrl)

            // Intentar primero endpoint unificado rápido
            val unifiedDeferred = coroutineScope {
                async {
                    try {
                        apiService.getUnifiedSchedule()
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            // O paralelizar Cinemark y Cine Colombia por teatros
            coroutineScope {
                val cinemarkDeferred = async {
                    try {
                        apiService.getCinemarkSchedule()
                    } catch (e: Exception) {
                        null
                    }
                }

                val cinecoAlegraDeferred = async {
                    try {
                        apiService.getCineColombiaSchedule("parque-alegra")
                    } catch (e: Exception) {
                        null
                    }
                }

                val cinecoBuenaDeferred = async {
                    try {
                        apiService.getCineColombiaSchedule("buenavista")
                    } catch (e: Exception) {
                        null
                    }
                }

                // 1. Apenas responda Cinemark, actualizamos inmediatamente el widget (Paso 1)
                val cinemarkResult = cinemarkDeferred.await()
                if (cinemarkResult != null) {
                    val partialCinemas = mutableListOf(cinemarkResult)
                    saveAndRefreshWidget(prefs, partialCinemas, isSyncing = true)
                }

                // 2. Esperar respuestas de Cine Colombia (Paso 2)
                val alegreResult = cinecoAlegraDeferred.await()
                val buenaResult = cinecoBuenaDeferred.await()

                val unifiedResult = unifiedDeferred.await()

                val finalCinemas = if (unifiedResult != null && unifiedResult.cinemas.isNotEmpty()) {
                    unifiedResult.cinemas
                } else {
                    val list = mutableListOf<CinemaSchedule>()
                    cinemarkResult?.let { list.add(it) }
                    alegreResult?.let { list.add(it) }
                    buenaResult?.let { list.add(it) }
                    list
                }

                if (finalCinemas.isNotEmpty()) {
                    saveAndRefreshWidget(prefs, finalCinemas, isSyncing = false)
                    Result.success()
                } else {
                    prefs.edit().putBoolean("is_syncing", false).apply()
                    CinemaWidget().updateAll(context)
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            prefs.edit().putBoolean("is_syncing", false).apply()
            CinemaWidget().updateAll(context)
            Result.retry()
        }
    }

    private suspend fun saveAndRefreshWidget(
        prefs: android.content.SharedPreferences,
        cinemas: List<CinemaSchedule>,
        isSyncing: Boolean
    ) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val unifiedResponse = UnifiedScheduleResponse(
            date = cinemas.firstOrNull()?.date ?: todayStr,
            updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
            cinemas = cinemas
        )
        val json = Gson().toJson(unifiedResponse)
        prefs.edit()
            .putString("last_schedule_json", json)
            .putBoolean("is_syncing", isSyncing)
            .apply()

        CinemaWidget().updateAll(context)
    }
}
