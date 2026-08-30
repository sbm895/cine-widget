package com.cinewidget.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback

class OpenBookingUrlActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val url = parameters[BookingUrlKey]
        if (!url.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    companion object {
        val BookingUrlKey = ActionParameters.Key<String>("destination_booking_url")

        fun createParameters(url: String): ActionParameters {
            return actionParametersOf(BookingUrlKey to url)
        }
    }
}
