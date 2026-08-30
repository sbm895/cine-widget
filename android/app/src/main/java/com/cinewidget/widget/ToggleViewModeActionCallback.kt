package com.cinewidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

class ToggleViewModeActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val targetMode = parameters[ViewModeKey]
        if (!targetMode.isNullOrBlank()) {
            val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("view_mode", targetMode).commit()
            CinemaWidget().updateAll(context)
        }
    }

    companion object {
        val ViewModeKey = ActionParameters.Key<String>("target_view_mode")

        fun createParameters(mode: String): ActionParameters {
            return actionParametersOf(ViewModeKey to mode)
        }
    }
}
