package com.cinewidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

class ToggleMovieAccordionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val movieKey = parameters[MovieKey] ?: return
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        val currentExpanded = prefs.getStringSet("expanded_movies", null)?.toMutableSet()
            ?: mutableSetOf()

        if (currentExpanded.contains(movieKey)) {
            currentExpanded.remove(movieKey)
        } else {
            currentExpanded.add(movieKey)
        }

        prefs.edit().putStringSet("expanded_movies", currentExpanded).commit()
        CinemaWidget().updateAll(context)
    }

    companion object {
        val MovieKey = ActionParameters.Key<String>("movie_key")

        fun createParameters(movieKey: String): ActionParameters {
            return actionParametersOf(MovieKey to movieKey)
        }
    }
}
