package com.cinewidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

class ToggleCinemaAccordionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val cinemaId = parameters[CinemaIdKey] ?: return
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        val currentExpanded = prefs.getStringSet("expanded_cinemas", null)?.toMutableSet()
            ?: mutableSetOf("cinemark-gran-plaza-del-sol")

        if (currentExpanded.contains(cinemaId)) {
            currentExpanded.remove(cinemaId)
        } else {
            currentExpanded.add(cinemaId)
        }

        prefs.edit().putStringSet("expanded_cinemas", currentExpanded).commit()
        CinemaWidget().update(context, glanceId)
    }

    companion object {
        val CinemaIdKey = ActionParameters.Key<String>("cinema_id")

        fun createParameters(cinemaId: String): ActionParameters {
            return actionParametersOf(CinemaIdKey to cinemaId)
        }
    }
}
