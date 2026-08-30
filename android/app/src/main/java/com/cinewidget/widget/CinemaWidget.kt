package com.cinewidget.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Scale
import com.cinewidget.R
import com.cinewidget.data.model.CinemaSchedule
import com.cinewidget.data.model.Movie
import com.cinewidget.data.model.Showtime
import com.cinewidget.data.model.UnifiedScheduleResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CinemaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    // Paleta de colores minimalista inspirada en la referencia:
    private val widgetBgColor = ColorProvider(Color(0xFF1E1A1D))        // Fondo oscuro profundo
    private val cardBgColor = ColorProvider(Color(0xFF282328))          // Tarjeta película elevada
    private val textPrimaryColor = ColorProvider(Color(0xFFF3EDF1))     // Texto blanco suave
    private val textSecondaryColor = ColorProvider(Color(0xFFA89FA6))   // Texto secundario atenuado
    private val textTertiaryColor = ColorProvider(Color(0xFF7E757D))    // Texto terciario / metadatos
    private val chipBgColor = ColorProvider(Color(0xFF332D34))          // Fondo chip de horario
    private val activeTabBg = ColorProvider(Color(0xFF4A414B))          // Tab selector activo
    private val cinemarkAccent = ColorProvider(Color(0xFFE50914))       // Acento Cinemark
    private val cinecoAccent = ColorProvider(Color(0xFF1E88E5))         // Acento Cine Colombia
    private val royalAccent = ColorProvider(Color(0xFFE5A00D))          // Acento Royal Films

    // Modelos aplanados para LazyColumn (Flat List)
    sealed interface CinemaFeedItem {
        data class CinemaHeader(
            val cinemaId: String,
            val name: String,
            val location: String,
            val accent: ColorProvider,
            val isExpanded: Boolean,
            val movieCount: Int
        ) : CinemaFeedItem
        data class MovieCardItem(val movie: Movie, val brandAccent: ColorProvider, val posterUrl: String?) : CinemaFeedItem
        data class EmptyCinemaMessage(val message: String) : CinemaFeedItem
        data class SafetyLimitMessage(val message: String) : CinemaFeedItem
    }

    sealed interface MovieFeedItem {
        data class MovieHeaderItem(
            val movieGroup: MovieGroup,
            val posterUrl: String?,
            val isExpanded: Boolean,
            val cinemaCount: Int
        ) : MovieFeedItem
        data class CinemaSubHeaderItem(val cinemaName: String, val location: String, val accent: ColorProvider) : MovieFeedItem
        data class ShowtimesRowItem(val showtimes: List<Showtime>) : MovieFeedItem
        object DividerItem : MovieFeedItem
        data class SafetyLimitMessage(val message: String) : MovieFeedItem
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        val isSyncing = prefs.getBoolean("is_syncing", false)
        val viewMode = prefs.getString("view_mode", "by_cinema") ?: "by_cinema" // "by_cinema" | "by_movie"
        val expandedCinemas = prefs.getStringSet("expanded_cinemas", null) ?: setOf("cinemark-gran-plaza-del-sol")
        val expandedMovies = prefs.getStringSet("expanded_movies", null) ?: emptySet()
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
                WidgetContent(schedule, isSyncing, viewMode, expandedCinemas, expandedMovies)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        schedule: UnifiedScheduleResponse?,
        isSyncing: Boolean,
        viewMode: String,
        expandedCinemas: Set<String>,
        expandedMovies: Set<String>
    ) {
        val context = androidx.glance.LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBgColor)
                .padding(12.dp)
        ) {
            // Header con Título y Botones (Abrir App + Actualizar)
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(androidx.glance.action.actionStartActivity(android.content.ComponentName(context, com.cinewidget.MainActivity::class.java)))
                ) {
                    Text(
                        text = "Cartelera ↗",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimaryColor
                        )
                    )
                    Text(
                        text = if (schedule != null && schedule.date.isNotBlank()) schedule.date else "Barranquilla y Soledad",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = textSecondaryColor
                        )
                    )
                }

                // Botón "Abrir App"
                Text(
                    text = "App",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = cinecoAccent
                    ),
                    modifier = GlanceModifier
                        .background(cardBgColor)
                        .clickable(androidx.glance.action.actionStartActivity(android.content.ComponentName(context, com.cinewidget.MainActivity::class.java)))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                // Botón "Actualizar"
                val refreshText = if (isSyncing) "Sincronizando..." else "Actualizar"
                val refreshModifier = if (!isSyncing) {
                    GlanceModifier
                        .background(cardBgColor)
                        .clickable(actionRunCallback<RefreshWidgetActionCallback>())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else {
                    GlanceModifier
                        .background(chipBgColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                }

                Text(
                    text = refreshText,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSyncing) textTertiaryColor else textSecondaryColor
                    ),
                    modifier = refreshModifier
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Selector de Modo (Tabs / Segmented Pill: [ Por Cine | Por Película ])
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(chipBgColor)
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isByCinema = viewMode == "by_cinema"

                // Opción 1: Por Cine
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(if (isByCinema) activeTabBg else chipBgColor)
                        .clickable(
                            actionRunCallback<ToggleViewModeActionCallback>(
                                ToggleViewModeActionCallback.createParameters("by_cinema")
                            )
                        )
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Por Cine",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = if (isByCinema) FontWeight.Bold else FontWeight.Normal,
                            color = if (isByCinema) textPrimaryColor else textSecondaryColor,
                            textAlign = TextAlign.Center
                        )
                    )
                }

                // Opción 2: Por Película
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(if (!isByCinema) activeTabBg else chipBgColor)
                        .clickable(
                            actionRunCallback<ToggleViewModeActionCallback>(
                                ToggleViewModeActionCallback.createParameters("by_movie")
                            )
                        )
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Por Película",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = if (!isByCinema) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isByCinema) textPrimaryColor else textSecondaryColor,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (schedule == null || schedule.cinemas.isEmpty()) {
                val emptyMessage = if (isSyncing) {
                    "Sincronizando cartelera en segundo plano..."
                } else {
                    "Sin funciones cargadas.\nToca para sincronizar."
                }

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionRunCallback<RefreshWidgetActionCallback>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyMessage,
                        style = TextStyle(
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = textSecondaryColor
                        )
                    )
                }
            } else {
                if (viewMode == "by_movie") {
                    // Modo Por Película: Acordeones interactivos por película con tope de 18
                    val flatMovieItems = buildFlatMovieList(schedule.cinemas, expandedMovies)
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        items(flatMovieItems) { item ->
                            when (item) {
                                is MovieFeedItem.MovieHeaderItem -> {
                                    MovieHeaderCard(item.movieGroup, item.isExpanded, item.cinemaCount)
                                    Spacer(modifier = GlanceModifier.height(4.dp))
                                }
                                is MovieFeedItem.CinemaSubHeaderItem -> {
                                    CinemaSubHeaderRow(item)
                                }
                                is MovieFeedItem.ShowtimesRowItem -> {
                                    ShowtimesRow(item.showtimes)
                                    Spacer(modifier = GlanceModifier.height(4.dp))
                                }
                                is MovieFeedItem.DividerItem -> {
                                    Spacer(modifier = GlanceModifier.height(8.dp))
                                }
                                is MovieFeedItem.SafetyLimitMessage -> {
                                    Text(
                                        text = item.message,
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            color = textTertiaryColor,
                                            textAlign = TextAlign.Center
                                        ),
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Modo Por Cine: Acordeones interactivos con tope seguro de 18 películas
                    val flatItems = buildFlatCinemaList(schedule.cinemas, expandedCinemas)
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        items(flatItems) { item ->
                            when (item) {
                                is CinemaFeedItem.CinemaHeader -> {
                                    CinemaHeaderRow(item)
                                    Spacer(modifier = GlanceModifier.height(4.dp))
                                }
                                is CinemaFeedItem.MovieCardItem -> {
                                    MovieCard(item.movie, item.brandAccent)
                                    Spacer(modifier = GlanceModifier.height(6.dp))
                                }
                                is CinemaFeedItem.EmptyCinemaMessage -> {
                                    Text(
                                        text = item.message,
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            color = textSecondaryColor
                                        ),
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .background(cardBgColor)
                                            .padding(10.dp)
                                    )
                                    Spacer(modifier = GlanceModifier.height(6.dp))
                                }
                                is CinemaFeedItem.SafetyLimitMessage -> {
                                    Text(
                                        text = item.message,
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            color = textTertiaryColor,
                                            textAlign = TextAlign.Center
                                        ),
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildFlatCinemaList(
        cinemas: List<CinemaSchedule>,
        expandedCinemas: Set<String>
    ): List<CinemaFeedItem> {
        val result = mutableListOf<CinemaFeedItem>()
        var renderedMovieCount = 0
        val maxMoviesLimit = 18

        cinemas.forEach { cinema ->
            val brandAccent = getCinemaAccent(cinema.cinemaName, cinema.cinemaId)
            val isExpanded = expandedCinemas.contains(cinema.cinemaId)

            result.add(
                CinemaFeedItem.CinemaHeader(
                    cinemaId = cinema.cinemaId,
                    name = cinema.cinemaName,
                    location = cinema.location,
                    accent = brandAccent,
                    isExpanded = isExpanded,
                    movieCount = cinema.movies.size
                )
            )

            if (isExpanded) {
                if (cinema.status == "error" || cinema.movies.isEmpty()) {
                    val msg = cinema.errorMessage ?: "Funciones no disponibles temporalmente."
                    result.add(CinemaFeedItem.EmptyCinemaMessage(msg))
                } else {
                    for (movie in cinema.movies) {
                        if (renderedMovieCount >= maxMoviesLimit) {
                            result.add(
                                CinemaFeedItem.SafetyLimitMessage(
                                    "⚡ Mostrando 18 películas activas. Colapsa un cine para explorar los demás con fluidez."
                                )
                            )
                            return result
                        }
                        result.add(CinemaFeedItem.MovieCardItem(movie, brandAccent, movie.coverImage))
                        renderedMovieCount++
                    }
                }
            }
        }
        return result
    }

    private fun buildFlatMovieList(
        cinemas: List<CinemaSchedule>,
        expandedMovies: Set<String>
    ): List<MovieFeedItem> {
        val groupedMovies = groupScheduleByMovie(cinemas)
        val result = mutableListOf<MovieFeedItem>()
        val maxMoviesLimit = 18
        var count = 0

        for (group in groupedMovies) {
            val normalizedKey = group.title.trim().lowercase().replace(".", "").replace(":", "")
            val isExpanded = expandedMovies.contains(normalizedKey)

            if (count >= maxMoviesLimit) {
                result.add(
                    MovieFeedItem.SafetyLimitMessage(
                        "⚡ Mostrando las 18 películas principales en cartelera."
                    )
                )
                break
            }

            result.add(
                MovieFeedItem.MovieHeaderItem(
                    movieGroup = group,
                    posterUrl = group.coverImage,
                    isExpanded = isExpanded,
                    cinemaCount = group.cinemaGroups.size
                )
            )

            if (isExpanded) {
                group.cinemaGroups.forEach { cinemaGroup ->
                    result.add(MovieFeedItem.CinemaSubHeaderItem(cinemaGroup.cinemaName, cinemaGroup.location, cinemaGroup.accent))
                    val chunked = cinemaGroup.showtimes.chunked(2)
                    chunked.forEach { rowShowtimes ->
                        result.add(MovieFeedItem.ShowtimesRowItem(rowShowtimes))
                    }
                }
            }

            result.add(MovieFeedItem.DividerItem)
            count++
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

    @Composable
    private fun CinemaHeaderRow(header: CinemaFeedItem.CinemaHeader) {
        val chevron = if (header.isExpanded) "▼" else "▶"

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBgColor)
                .clickable(
                    actionRunCallback<ToggleCinemaAccordionCallback>(
                        ToggleCinemaAccordionCallback.createParameters(header.cinemaId)
                    )
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$chevron  ",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = header.accent
                )
            )

            Text(
                text = header.name.uppercase(),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimaryColor
                )
            )

            Text(
                text = "  ${header.location}",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    color = textTertiaryColor
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            if (header.movieCount > 0) {
                Text(
                    text = "${header.movieCount} pelis",
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = textTertiaryColor
                    ),
                    modifier = GlanceModifier
                        .background(chipBgColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }

    @Composable
    private fun MovieCard(movie: Movie, brandAccent: ColorProvider) {
        val metadata = buildList {
            movie.rating?.takeIf { it.isNotBlank() }?.let { add(it) }
            movie.durationMinutes?.let { add("${it} min") }
            movie.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBgColor)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Indicador de barra vertical con el acento del cine
            Box(
                modifier = GlanceModifier
                    .width(3.dp)
                    .height(36.dp)
                    .background(brandAccent)
            ) {}

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Información y Grid de Horarios
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
            ) {
                Text(
                    text = movie.title,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    )
                )

                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = textTertiaryColor
                        ),
                        modifier = GlanceModifier.padding(top = 1.dp, bottom = 6.dp)
                    )
                }

                // Cuadrícula de 2 columnas de horarios
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
                                ShowtimeChip(showtime)
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = GlanceModifier.defaultWeight().padding(horizontal = 2.dp))
                        }
                    }
                }
            }
        }
    }

    // Estructura de soporte para vista "Por Película"
    data class CinemaShowtimeGroup(
        val cinemaName: String,
        val location: String,
        val accent: ColorProvider,
        val showtimes: List<Showtime>
    )

    data class MovieGroup(
        val title: String,
        val rating: String?,
        val durationMinutes: Int?,
        val genre: String?,
        val coverImage: String?,
        val cinemaGroups: List<CinemaShowtimeGroup>
    )

    private fun groupScheduleByMovie(cinemas: List<CinemaSchedule>): List<MovieGroup> {
        val movieMap = linkedMapOf<String, MovieGroup>()

        cinemas.forEach { cinema ->
            val accent = getCinemaAccent(cinema.cinemaName, cinema.cinemaId)
            cinema.movies.forEach { movie ->
                val normalizedKey = movie.title.trim().lowercase().replace(".", "").replace(":", "")
                val existing = movieMap[normalizedKey]
                val currentGroup = CinemaShowtimeGroup(
                    cinemaName = cinema.cinemaName,
                    location = cinema.location,
                    accent = accent,
                    showtimes = movie.showtimes
                )

                if (existing != null) {
                    movieMap[normalizedKey] = existing.copy(
                        rating = existing.rating ?: movie.rating,
                        durationMinutes = existing.durationMinutes ?: movie.durationMinutes,
                        genre = existing.genre ?: movie.genre,
                        coverImage = existing.coverImage ?: movie.coverImage,
                        cinemaGroups = existing.cinemaGroups + currentGroup
                    )
                } else {
                    movieMap[normalizedKey] = MovieGroup(
                        title = movie.title,
                        rating = movie.rating,
                        durationMinutes = movie.durationMinutes,
                        genre = movie.genre,
                        coverImage = movie.coverImage,
                        cinemaGroups = listOf(currentGroup)
                    )
                }
            }
        }

        return movieMap.values.toList()
    }

    @Composable
    private fun MovieHeaderCard(
        movieGroup: MovieGroup,
        isExpanded: Boolean,
        cinemaCount: Int
    ) {
        val metadata = buildList {
            movieGroup.rating?.takeIf { it.isNotBlank() }?.let { add(it) }
            movieGroup.durationMinutes?.let { add("${it} min") }
            movieGroup.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")

        val normalizedKey = movieGroup.title.trim().lowercase().replace(".", "").replace(":", "")
        val chevron = if (isExpanded) "▼" else "▶"

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBgColor)
                .clickable(
                    actionRunCallback<ToggleMovieAccordionCallback>(
                        ToggleMovieAccordionCallback.createParameters(normalizedKey)
                    )
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$chevron  ",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = cinecoAccent
                )
            )

            Column(
                modifier = GlanceModifier.defaultWeight()
            ) {
                Text(
                    text = movieGroup.title,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    )
                )
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata,
                        style = TextStyle(
                            fontSize = 9.sp,
                            color = textTertiaryColor
                        )
                    )
                }
            }

            if (cinemaCount > 0) {
                Text(
                    text = "$cinemaCount cines",
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = textTertiaryColor
                    ),
                    modifier = GlanceModifier
                        .background(chipBgColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }

    @Composable
    private fun CinemaSubHeaderRow(item: MovieFeedItem.CinemaSubHeaderItem) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "•",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = item.accent
                ),
                modifier = GlanceModifier.padding(end = 4.dp)
            )
            Text(
                text = "${item.cinemaName} (${item.location})",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondaryColor
                )
            )
        }
    }

    @Composable
    private fun ShowtimesRow(showtimes: List<Showtime>) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            showtimes.forEach { showtime ->
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 2.dp)
                ) {
                    ShowtimeChip(showtime)
                }
            }
            if (showtimes.size == 1) {
                Spacer(modifier = GlanceModifier.defaultWeight().padding(horizontal = 2.dp))
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
            if (screenInfo.isNotEmpty()) append(" $screenInfo")
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
                .background(chipBgColor)
                .then(actionModifier)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = chipText,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimaryColor,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}
