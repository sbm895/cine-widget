package com.cinewidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cinewidget.worker.ScheduleUpdateWorker

class RefreshWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_syncing", true).apply()
        CinemaWidget().updateAll(context)

        val request = OneTimeWorkRequestBuilder<ScheduleUpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
