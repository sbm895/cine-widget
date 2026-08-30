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
import com.cinewidget.data.model.CinemaSchedule
import com.cinewidget.data.model.Movie
import com.cinewidget.data.model.Showtime
import com.cinewidget.data.model.UnifiedScheduleResponse
import com.google.gson.Gson

class CinemaWidget : GlanceAppWidget() {

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

    // Lista plana para el LazyColumn - sin estado de acordeones
    sealed interface CinemaFeedItem {
        data class CinemaHeader(
            val cinemaName: String,
            val location: String,
            val accent: ColorProvider,
            val movieCount: Int
        ) : CinemaFeedItem
        data class MovieRow(val movie: Movie, val accent: ColorProvider) : CinemaFeedItem
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        val isSyncing = prefs.getBoolean("is_syncing", false)
        val cachedJson = prefs.getString("last_schedule_json", null)
        val schedule = if (cachedJson != null) {
            try { Gson().fromJson(cachedJson, UnifiedScheduleResponse::class.java) }
            catch (e: Exception) { null }
        } else { null }

        provideContent {
            GlanceTheme {
                WidgetContent(schedule, isSyncing)
            }
        }
    }

    @Composable
    private fun WidgetContent(schedule: UnifiedScheduleResponse?, isSyncing: Boolean) {
        val context = LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBgColor)
                .padding(12.dp)
        ) {
            // Header: Título + Botón Actualizar
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))
                ) {
                    Text(
                        text = "Cartelera \u2197",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimaryColor
                        )
                    )
                    Text(
                        text = if (schedule != null && schedule.date.isNotBlank()) schedule.date else "Barranquilla y Soledad",
                        style = TextStyle(fontSize = 11.sp, color = textSecondaryColor)
                    )
                }

                Text(
                    text = "App",
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = cinecoAccent),
                    modifier = GlanceModifier
                        .background(cardBgColor)
                        .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                Text(
                    text = if (isSyncing) "Sincronizando..." else "Actualizar",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSyncing) textTertiaryColor else textSecondaryColor
                    ),
                    modifier = if (!isSyncing) {
                        GlanceModifier
                            .background(cardBgColor)
                            .clickable(actionRunCallback<RefreshWidgetActionCallback>())
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    } else {
                        GlanceModifier.background(chipBgColor).padding(horizontal = 8.dp, vertical = 4.dp)
                    }
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            if (schedule == null || schedule.cinemas.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionRunCallback<RefreshWidgetActionCallback>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSyncing) "Sincronizando cartelera..." else "Sin funciones cargadas.\nToca Actualizar o abre la app.",
                        style = TextStyle(
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = textSecondaryColor
                        )
                    )
                }
            } else {
                val flatItems = buildFlatList(schedule.cinemas)
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(flatItems) { item ->
                        when (item) {
                            is CinemaFeedItem.CinemaHeader -> {
                                CinemaHeaderRow(item)
                                Spacer(modifier = GlanceModifier.height(4.dp))
                            }
                            is CinemaFeedItem.MovieRow -> {
                                MovieCompactRow(item.movie, item.accent)
                                Spacer(modifier = GlanceModifier.height(3.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CinemaHeaderRow(item: CinemaFeedItem.CinemaHeader) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBgColor)
                .clickable(actionRunCallback<RefreshWidgetActionCallback>())
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .width(4.dp)
                    .height(28.dp)
                    .background(item.accent)
            ) {}

            Spacer(modifier = GlanceModifier.width(8.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = item.cinemaName.uppercase(),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    )
                )
                Text(
                    text = item.location,
                    style = TextStyle(fontSize = 9.sp, color = textTertiaryColor)
                )
            }

            Text(
                text = "${item.movieCount} pelis",
                style = TextStyle(fontSize = 9.sp, color = textTertiaryColor),
                modifier = GlanceModifier
                    .background(chipBgColor)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }

    @Composable
    private fun MovieCompactRow(movie: Movie, accent: ColorProvider) {
        // Tomamos máximo 4 horarios para mantener el payload bajo
        val showtimes = movie.showtimes.take(4)

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 0.dp, top = 0.dp, bottom = 0.dp)
        ) {
            // Título de la película
            Text(
                text = movie.title,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimaryColor
                ),
                modifier = GlanceModifier.padding(start = 4.dp, bottom = 2.dp)
            )

            // Chips de horarios en filas de 2
            val chunked = showtimes.chunked(2)
            chunked.forEach { rowItems ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowItems.forEach { showtime ->
                        Box(modifier = GlanceModifier.defaultWeight().padding(end = 3.dp)) {
                            ShowtimeChip(showtime)
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }

    @Composable
    private fun ShowtimeChip(showtime: Showtime) {
        val availabilityText = when {
            showtime.seatsAvailable == null -> "Boletos"
            showtime.seatsAvailable > 0 -> "${showtime.seatsAvailable}"
            else -> "Agotado"
        }

        val screenInfo = (showtime.screenTypes + listOfNotNull(showtime.language))
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val chipText = buildString {
            append(showtime.time)
            if (screenInfo.isNotEmpty()) append(" $screenInfo")
            append(" $availabilityText")
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
                .background(chipBgColor)
                .then(actionModifier)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = chipText,
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimaryColor,
                    textAlign = TextAlign.Center
                )
            )
        }
    }

    private fun buildFlatList(cinemas: List<CinemaSchedule>): List<CinemaFeedItem> {
        val result = mutableListOf<CinemaFeedItem>()
        cinemas.forEach { cinema ->
            val accent = getCinemaAccent(cinema.cinemaName, cinema.cinemaId)
            result.add(
                CinemaFeedItem.CinemaHeader(
                    cinemaName = cinema.cinemaName,
                    location = cinema.location,
                    accent = accent,
                    movieCount = cinema.movies.size
                )
            )
            // Máximo 5 películas por cine para mantener el payload Binder bajo
            cinema.movies.take(5).forEach { movie ->
                result.add(CinemaFeedItem.MovieRow(movie = movie, accent = accent))
            }
        }
        return result
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
