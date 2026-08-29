package com.cinewidget.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.cinewidget.data.model.CinemaSchedule
import com.cinewidget.data.model.Movie
import com.cinewidget.data.model.Showtime
import com.cinewidget.data.model.UnifiedScheduleResponse
import com.cinewidget.worker.ScheduleUpdateWorker
import com.google.gson.Gson

class CinemaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        val cachedJson = prefs.getString("last_schedule_json", null)
        val schedule = if (cachedJson != null) {
            try {
                Gson().fromJson(cachedJson, UnifiedScheduleResponse::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        provideContent {
            GlanceTheme {
                WidgetContent(schedule)
            }
        }
    }

    @Composable
    private fun WidgetContent(schedule: UnifiedScheduleResponse?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(12.dp)
        ) {
            // Header del Widget
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎬 Cartelera de Cines",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onBackground
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                if (schedule != null) {
                    Text(
                        text = schedule.date,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.secondary
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (schedule == null || schedule.cinemas.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cargando horarios...\nToca para actualizar.",
                        style = TextStyle(
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = GlanceTheme.colors.onSurfaceVariant
                        ),
                        modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetActionCallback>())
                    )
                }
            } else {
                LazyColumn(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    items(schedule.cinemas) { cinema ->
                        CinemaSection(cinema)
                    }
                }
            }
        }
    }

    @Composable
    private fun CinemaSection(cinema: CinemaSchedule) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            // Título del Cine
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(GlanceTheme.colors.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${cinema.cinemaName.uppercase()} · ${cinema.location}",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onPrimaryContainer
                    )
                )
            }

            if (cinema.status == "error" || cinema.movies.isEmpty()) {
                Text(
                    text = cinema.errorMessage ?: "No hay funciones disponibles por ahora.",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.error
                    ),
                    modifier = GlanceModifier.padding(8.dp)
                )
            } else {
                cinema.movies.forEach { movie ->
                    MovieItem(movie)
                }
            }
        }
    }

    @Composable
    private fun MovieItem(movie: Movie) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Text(
                text = movie.title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlanceTheme.colors.onBackground
                )
            )

            // Fila de chips de horarios
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                movie.showtimes.forEach { showtime ->
                    ShowtimeChip(showtime)
                }
            }
        }
    }

    @Composable
    private fun ShowtimeChip(showtime: Showtime) {
        val seatsBadge = when {
            showtime.seatsAvailable == null -> ""
            showtime.seatsAvailable > 50 -> "🟢 ${showtime.seatsAvailable}"
            showtime.seatsAvailable > 0 -> "🟡 ${showtime.seatsAvailable}"
            else -> "🔴 Agotado"
        }

        val screenInfo = (showtime.screenTypes + listOfNotNull(showtime.language)).joinToString(" · ")
        val label = buildString {
            append(showtime.time)
            if (screenInfo.isNotEmpty()) append(" ($screenInfo)")
            if (seatsBadge.isNotEmpty()) append(" $seatsBadge")
        }

        val intent = showtime.bookingUrl?.let { url ->
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        Row(
            modifier = GlanceModifier
                .padding(end = 6.dp, top = 2.dp, bottom = 2.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(
                    if (intent != null) actionStartActivity(intent)
                    else actionRunCallback<RefreshWidgetActionCallback>()
                )
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}
