package com.cinewidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import com.google.gson.Gson

class CinemaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

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
                .padding(10.dp)
        ) {
            // Header del Widget
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CARTELERA DE CINES",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                if (schedule != null && schedule.date.isNotBlank()) {
                    Text(
                        text = schedule.date,
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.secondary
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                Text(
                    text = "Actualizar",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary
                    ),
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .clickable(actionRunCallback<RefreshWidgetActionCallback>())
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            if (schedule == null || schedule.cinemas.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionRunCallback<RefreshWidgetActionCallback>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin datos disponibles.\nToca aquí para sincronizar.",
                        style = TextStyle(
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
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
                .padding(vertical = 4.dp)
        ) {
            // Barra de título del cine
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(GlanceTheme.colors.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${cinema.cinemaName.uppercase()}  |  ${cinema.location}",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }

            if (cinema.status == "error" || cinema.movies.isEmpty()) {
                Text(
                    text = cinema.errorMessage ?: "Funciones no disponibles temporalmente.",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.error
                    ),
                    modifier = GlanceModifier.padding(horizontal = 8.dp, vertical = 6.dp)
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
        val metadata = buildList {
            movie.rating?.takeIf { it.isNotBlank() }?.let { add(it) }
            movie.durationMinutes?.let { add("${it} min") }
            movie.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString("  •  ")

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp, horizontal = 4.dp)
        ) {
            Text(
                text = movie.title,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onBackground
                )
            )

            if (metadata.isNotEmpty()) {
                Text(
                    text = metadata,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.secondary
                    ),
                    modifier = GlanceModifier.padding(bottom = 2.dp)
                )
            }

            // Horarios de la película
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
        val availabilityText = when {
            showtime.seatsAvailable == null -> "Boletos"
            showtime.seatsAvailable > 0 -> "${showtime.seatsAvailable} disp."
            else -> "Agotado"
        }

        val screenInfo = (showtime.screenTypes + listOfNotNull(showtime.language))
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val chipText = buildString {
            append(showtime.time)
            if (screenInfo.isNotEmpty()) append(" [$screenInfo]")
            append(" · $availabilityText")
        }

        val actionModifier = if (!showtime.bookingUrl.isNullOrBlank()) {
            GlanceModifier.clickable(
                actionRunCallback<OpenBookingUrlActionCallback>(
                    OpenBookingUrlActionCallback.createParameters(showtime.bookingUrl)
                )
            )
        } else {
            GlanceModifier.clickable(actionRunCallback<RefreshWidgetActionCallback>())
        }

        Row(
            modifier = GlanceModifier
                .padding(end = 4.dp, top = 2.dp, bottom = 2.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .then(actionModifier)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chipText,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}
