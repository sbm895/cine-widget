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

    // Lista plana por pelicula
    data class MovieGroup(
        val title: String,
        val rating: String?,
        val durationMinutes: Int?,
        val genre: String?,
        val coverImage: String?
    )

    data class CinemaShowtimeGroup(
        val cinemaName: String,
        val location: String,
        val accent: ColorProvider,
        val showtimes: List<Showtime>
    )

    sealed interface MovieFeedItem {
        data class MovieHeader(val group: MovieGroup, val cinemaCount: Int) : MovieFeedItem
        data class CinemaRow(val csg: CinemaShowtimeGroup) : MovieFeedItem
        data class ShowtimesRow(val showtimes: List<Showtime>) : MovieFeedItem
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = try {
            context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        } catch (e: Exception) { null }

        val isSyncing = prefs?.getBoolean("is_syncing", false) ?: false
        val cachedJson = prefs?.getString("last_schedule_json", null)
        val schedule = if (!cachedJson.isNullOrBlank()) {
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
            // Header: Titulo + App + Actualizar
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
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimaryColor)
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
                    modifier = if (!isSyncing) GlanceModifier
                        .background(cardBgColor)
                        .clickable(actionRunCallback<RefreshWidgetActionCallback>())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                    else GlanceModifier.background(chipBgColor).padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            if (schedule == null || schedule.cinemas.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java))),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isSyncing) "\u23F3 Sincronizando..." else "\uD83C\uDFAC Cartelera",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimaryColor, textAlign = TextAlign.Center)
                        )
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        Text(
                            text = if (isSyncing) "Cargando funciones de hoy..." else "Abre la app para\nsincronizar la cartelera",
                            style = TextStyle(fontSize = 11.sp, color = textSecondaryColor, textAlign = TextAlign.Center)
                        )
                        Spacer(modifier = GlanceModifier.height(10.dp))
                        if (!isSyncing) {
                            Text(
                                text = "  Abrir app  ",
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cinecoAccent),
                                modifier = GlanceModifier
                                    .background(cardBgColor)
                                    .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            } else {
                val flatItems = buildFlatMovieList(schedule.cinemas)
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(flatItems) { item ->
                        when (item) {
                            is MovieFeedItem.MovieHeader -> {
                                MovieHeaderRow(item)
                                Spacer(modifier = GlanceModifier.height(2.dp))
                            }
                            is MovieFeedItem.CinemaRow -> {
                                CinemaSubRow(item.csg)
                            }
                            is MovieFeedItem.ShowtimesRow -> {
                                ShowtimesCompactRow(item.showtimes)
                                Spacer(modifier = GlanceModifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MovieHeaderRow(item: MovieFeedItem.MovieHeader) {
        val metadata = buildList {
            item.group.rating?.takeIf { it.isNotBlank() }?.let { add(it) }
            item.group.durationMinutes?.let { add("${it} min") }
            item.group.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" \u00B7 ")

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBgColor)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = item.group.title,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimaryColor)
                )
                if (metadata.isNotEmpty()) {
                    Text(text = metadata, style = TextStyle(fontSize = 9.sp, color = textTertiaryColor))
                }
            }
            if (item.cinemaCount > 0) {
                Text(
                    text = "${item.cinemaCount} cines",
                    style = TextStyle(fontSize = 9.sp, color = textTertiaryColor),
                    modifier = GlanceModifier
                        .background(chipBgColor)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }

    @Composable
    private fun CinemaSubRow(csg: CinemaShowtimeGroup) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 3.dp, bottom = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = GlanceModifier.width(3.dp).height(14.dp).background(csg.accent)) {}
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "${csg.cinemaName} \u00B7 ${csg.location}",
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textSecondaryColor)
            )
        }
    }

    @Composable
    private fun ShowtimesCompactRow(showtimes: List<Showtime>) {
        val chunked = showtimes.take(4).chunked(2)
        Column(modifier = GlanceModifier.fillMaxWidth().padding(start = 10.dp, bottom = 2.dp)) {
            chunked.forEach { rowItems ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowItems.forEach { showtime ->
                        Box(modifier = GlanceModifier.defaultWeight().padding(end = 3.dp)) {
                            ShowtimeChip(showtime)
                        }
                    }
                    if (rowItems.size == 1) Spacer(modifier = GlanceModifier.defaultWeight())
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
            .filter { it.isNotBlank() }.joinToString(" ")
        val chipText = buildString {
            append(showtime.time)
            if (screenInfo.isNotEmpty()) append(" $screenInfo")
            append(" $availabilityText")
        }
        val actionMod = if (!showtime.bookingUrl.isNullOrBlank()) {
            GlanceModifier.clickable(actionRunCallback<OpenBookingUrlActionCallback>(
                OpenBookingUrlActionCallback.createParameters(showtime.bookingUrl)
            ))
        } else GlanceModifier.clickable(actionRunCallback<RefreshWidgetActionCallback>())

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(chipBgColor)
                .then(actionMod)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = chipText,
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, color = textPrimaryColor, textAlign = TextAlign.Center)
            )
        }
    }

    private fun buildFlatMovieList(cinemas: List<CinemaSchedule>): List<MovieFeedItem> {
        // Agrupar por pelicula
        val movieMap = linkedMapOf<String, Triple<MovieGroup, Int, List<Pair<CinemaShowtimeGroup, List<Showtime>>>>>()

        cinemas.forEach { cinema ->
            val accent = getCinemaAccent(cinema.cinemaName, cinema.cinemaId)
            cinema.movies.forEach { movie ->
                val key = movie.title.trim().lowercase().replace(".", "").replace(":", "")
                val csg = CinemaShowtimeGroup(cinema.cinemaName, cinema.location, accent, movie.showtimes)
                val existing = movieMap[key]
                if (existing != null) {
                    val (group, _, list) = existing
                    movieMap[key] = Triple(group, list.size + 1, list + (csg to movie.showtimes))
                } else {
                    val group = MovieGroup(movie.title, movie.rating, movie.durationMinutes, movie.genre, movie.coverImage)
                    movieMap[key] = Triple(group, 1, listOf(csg to movie.showtimes))
                }
            }
        }

        val result = mutableListOf<MovieFeedItem>()
        // Maximo 8 peliculas para mantener el payload bajo
        movieMap.values.take(8).forEach { (group, cinemaCount, cinemaShowtimes) ->
            result.add(MovieFeedItem.MovieHeader(group, cinemaCount))
            cinemaShowtimes.forEach { (csg, showtimes) ->
                result.add(MovieFeedItem.CinemaRow(csg))
                result.add(MovieFeedItem.ShowtimesRow(showtimes))
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
