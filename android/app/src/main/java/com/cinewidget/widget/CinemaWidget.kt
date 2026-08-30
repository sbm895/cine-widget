package com.cinewidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
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
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    schedule.cinemas.forEach { cinema ->
                        CinemaSection(cinema)
                    }
                }
            }
        }
    }

    @Composable
    private fun CinemaSection(cinema: CinemaSchedule) {
        val isCinemark = cinema.cinemaName.contains("cinemark", ignoreCase = true) ||
                cinema.cinemaId.contains("cinemark", ignoreCase = true)

        // Color coding por cine:
        // Cinemark: Tonos Rojizo / Borgoña
        // Cine Colombia: Tonos Azul / Marino
        val headerBg = if (isCinemark) ColorProvider(Color(0xFFE50914)) else ColorProvider(Color(0xFF003B73))
        val headerText = ColorProvider(Color(0xFFFFFFFF))
        val cardBorderBg = if (isCinemark) ColorProvider(Color(0x26E50914)) else ColorProvider(Color(0x26003B73))

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            // Header del Complejo de Cine con su Color de Marca
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cinema.cinemaName.uppercase(),
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = headerText
                    )
                )

                Text(
                    text = "  |  ${cinema.location}",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = headerText
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            if (cinema.status == "error" || cinema.movies.isEmpty()) {
                Text(
                    text = cinema.errorMessage ?: "Funciones no disponibles temporalmente.",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.error
                    ),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(cardBorderBg)
                        .padding(10.dp)
                )
            } else {
                cinema.movies.forEach { movie ->
                    MovieCard(movie, isCinemark, cardBorderBg)
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun MovieCard(movie: Movie, isCinemark: Boolean, cardBorderBg: ColorProvider) {
        val metadata = buildList {
            movie.rating?.takeIf { it.isNotBlank() }?.let { add(it) }
            movie.durationMinutes?.let { add("${it} min") }
            movie.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString("  •  ")

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBorderBg)
                .padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Columna 1 (Izquierda): Placeholder / Contenedor del Póster de la Película
            Box(
                modifier = GlanceModifier
                    .width(48.dp)
                    .height(72.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "POSTER",
                    style = TextStyle(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Columna 2 (Derecha): Título, Metadatos y Cuadrícula de Horarios
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
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
                        modifier = GlanceModifier.padding(top = 1.dp, bottom = 4.dp)
                    )
                }

                // Horarios distribuidos en filas de 2 columnas (Cuadrícula balanceada)
                val chunkedShowtimes = movie.showtimes.chunked(2)
                chunkedShowtimes.forEach { rowItems ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowItems.forEach { showtime ->
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .padding(horizontal = 2.dp)
                            ) {
                                ShowtimeChip(showtime, isCinemark)
                            }
                        }
                        // Si la fila tiene solo 1 elemento, rellenamos el espacio restante
                        if (rowItems.size == 1) {
                            Spacer(modifier = GlanceModifier.defaultWeight().padding(horizontal = 2.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ShowtimeChip(showtime: Showtime, isCinemark: Boolean) {
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
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .then(actionModifier)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = chipText,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}
