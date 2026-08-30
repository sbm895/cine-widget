package com.cinewidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cinewidget.worker.ScheduleUpdateWorker

import androidx.work.workDataOf

class RefreshWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        val isAlreadySyncing = prefs.getBoolean("is_syncing", false)
        if (isAlreadySyncing) {
            return // Ignorar peticiones duplicadas / spam mientras está en proceso
        }

        // Bloquear el botón y mostrar estado "Sincronizando..." inmediatamente
        prefs.edit().putBoolean("is_syncing", true).commit()
        CinemaWidget().updateAll(context)

        val request = OneTimeWorkRequestBuilder<ScheduleUpdateWorker>()
            .setInputData(workDataOf("force_refresh" to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "manual_cinema_schedule_refresh",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
