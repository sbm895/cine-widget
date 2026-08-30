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

                val royalVivaDeferred = async {
                    try {
                        apiService.getRoyalFilmsSchedule("viva")
                    } catch (e: Exception) {
                        null
                    }
                }

                val royalPortalDeferred = async {
                    try {
                        apiService.getRoyalFilmsSchedule("portal-del-prado")
                    } catch (e: Exception) {
                        null
                    }
                }

                val royalUnicoDeferred = async {
                    try {
                        apiService.getRoyalFilmsSchedule("unico")
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

                // 1. Apenas respondan los endpoints rápidos (Cinemark y Royal Films), actualizamos de inmediato
                val cinemarkResult = cinemarkDeferred.await()
                val royalVivaResult = royalVivaDeferred.await()
                val royalPortalResult = royalPortalDeferred.await()
                val royalUnicoResult = royalUnicoDeferred.await()

                val initialFastList = listOfNotNull(cinemarkResult, royalVivaResult, royalPortalResult, royalUnicoResult)
                if (initialFastList.isNotEmpty()) {
                    saveAndRefreshWidget(prefs, initialFastList, isSyncing = true)
                }

                // 2. Esperar respuestas de Cine Colombia y unificado
                val alegreResult = cinecoAlegraDeferred.await()
                val buenaResult = cinecoBuenaDeferred.await()

                val unifiedResult = unifiedDeferred.await()

                val finalCinemas = if (unifiedResult != null && unifiedResult.cinemas.isNotEmpty()) {
                    unifiedResult.cinemas
                } else {
                    listOfNotNull(
                        cinemarkResult,
                        royalVivaResult,
                        royalPortalResult,
                        royalUnicoResult,
                        alegreResult,
                        buenaResult
                    )
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
