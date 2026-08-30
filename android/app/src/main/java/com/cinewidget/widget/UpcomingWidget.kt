package com.cinewidget.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
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
import androidx.glance.unit.ColorProvider
import com.cinewidget.MainActivity
import com.cinewidget.data.model.Showtime
import com.cinewidget.data.model.UnifiedScheduleResponse
import com.google.gson.Gson

data class UpcomingShowtimeItem(
    val movieTitle: String,
    val cinemaName: String,
    val cinemaLocation: String,
    val accent: ColorProvider,
    val showtime: Showtime
)

class UpcomingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    private val widgetBgColor = ColorProvider(Color(0xFF1E1A1D))
    private val cardBgColor = ColorProvider(Color(0xFF282328))
    private val textPrimaryColor = ColorProvider(Color(0xFFF3EDF1))
    private val textSecondaryColor = ColorProvider(Color(0xFFA89FA6))
    private val textTertiaryColor = ColorProvider(Color(0xFF7E757D))
    private val chipBgColor = ColorProvider(Color(0xFF332D34))
    private val cinemarkAccent = ColorProvider(Color(0xFFE50914))
    private val cinecoAccent = ColorProvider(Color(0xFF1E88E5))
    private val royalAccent = ColorProvider(Color(0xFFE5A00D))

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
                UpcomingContent(schedule)
            }
        }
    }

    @Composable
    private fun UpcomingContent(schedule: UnifiedScheduleResponse?) {
        val context = LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBgColor)
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java))),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "? Próximas Funciones ?",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "Hoy",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = cinecoAccent
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (schedule == null || schedule.cinemas.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin cartelera cargada.\nToca para sincronizar en la app.",
                        style = TextStyle(
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = textSecondaryColor
                        )
                    )
                }
                return@Column
            }

            // Extraer y ordenar funciones cronológicamente
            val upcomingItems = schedule.cinemas.flatMap { cinema ->
                val accent = getCinemaAccent(cinema.cinemaName, cinema.cinemaId)
                cinema.movies.flatMap { movie ->
                    movie.showtimes.map { showtime ->
                        UpcomingShowtimeItem(
                            movieTitle = movie.title,
                            cinemaName = cinema.cinemaName,
                            cinemaLocation = cinema.location,
                            accent = accent,
                            showtime = showtime
                        )
                    }
                }
            }
            .sortedBy { it.showtime.time }
            .take(20)

            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(upcomingItems) { item ->
                    UpcomingRow(item)
                    Spacer(modifier = GlanceModifier.height(5.dp))
                }
            }
        }
    }

    @Composable
    private fun UpcomingRow(item: UpcomingShowtimeItem) {
        val showtime = item.showtime
        val availabilityText = when {
            showtime.seatsAvailable == null -> "Boletos"
            showtime.seatsAvailable > 0 -> "${showtime.seatsAvailable} disp."
            else -> "Agotado"
        }

        val screenInfo = (showtime.screenTypes + listOfNotNull(showtime.language))
            .filter { it.isNotBlank() }
            .joinToString(" ")

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
                .background(cardBgColor)
                .then(actionModifier)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hora destacada
            Text(
                text = showtime.time,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimaryColor
                )
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Barra de cine
            Box(
                modifier = GlanceModifier
                    .width(3.dp)
                    .height(24.dp)
                    .background(item.accent)
            ) {}

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Información de Película y Cine
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = item.movieTitle,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    )
                )
                Text(
                    text = "${item.cinemaName} (${item.cinemaLocation}) · $screenInfo",
                    style = TextStyle(
                        fontSize = 9.sp,
                        color = textTertiaryColor
                    )
                )
            }

            // Disponibilidad
            Text(
                text = availabilityText,
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (showtime.seatsAvailable != null && showtime.seatsAvailable > 0) cinecoAccent else textTertiaryColor
                ),
                modifier = GlanceModifier
                    .background(chipBgColor)
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            )
        }
    }

    private fun getCinemaAccent(cinemaName: String, cinemaId: String): ColorProvider {
        val lowerName = cinemaName.lowercase()
        val lowerId = cinemaId.lowercase()
        return when {
            lowerName.contains("cinemark") || lowerId.contains("cinemark") -> cinemarkAccent
            lowerName.contains("royal") || lowerId.contains("royal") -> royalAccent
            else -> cinecoAccent
        }
    }
}
