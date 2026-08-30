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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        val isSyncing = prefs.getBoolean("is_syncing", false)
        val viewMode = prefs.getString("view_mode", "by_cinema") ?: "by_cinema" // "by_cinema" | "by_movie"
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

        // Descargar pósters en segundo plano con Coil (redimensionados e inyectando Referer para MovieXchange)
        val posterBitmaps = withContext(Dispatchers.IO) {
            val map = mutableMapOf<String, Bitmap>()
            if (schedule != null) {
                val okHttpClient = okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val original = chain.request()
                        val reqBuilder = original.newBuilder()
                        if (original.url.host.contains("moviexchange.com")) {
                            reqBuilder.header("Referer", "https://www.cinecolombia.com/")
                        }
                        chain.proceed(reqBuilder.build())
                    }
                    .build()

                val imageLoader = ImageLoader.Builder(context)
                    .okHttpClient(okHttpClient)
                    .build()

                schedule.cinemas.flatMap { it.movies }.forEach { movie ->
                    val url = movie.coverImage
                    if (!url.isNullOrBlank() && !map.containsKey(url)) {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .size(120, 180)
                                .scale(Scale.FIT)
                                .allowHardware(false)
                                .build()
                            val drawable = imageLoader.execute(request).drawable
                            drawable?.toBitmap()?.let { bitmap ->
                                map[url] = bitmap
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            map
        }

        provideContent {
            GlanceTheme {
                WidgetContent(schedule, isSyncing, viewMode, posterBitmaps)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        schedule: UnifiedScheduleResponse?,
        isSyncing: Boolean,
        viewMode: String,
        posterBitmaps: Map<String, Bitmap>
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBgColor)
                .padding(14.dp)
        ) {
            // Header con Título y Botón Actualizar
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier.defaultWeight()
                ) {
                    Text(
                        text = "Cartelera",
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

            // Selector de Modo (Tabs / Segmented Pill: [ Cines | Películas ])
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
                    // Modo Por Película: Agrupar cines y horarios por película
                    val groupedMovies = groupScheduleByMovie(schedule.cinemas)
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        items(groupedMovies) { movieGroup ->
                            MovieGroupedCard(movieGroup, posterBitmaps)
                            Spacer(modifier = GlanceModifier.height(8.dp))
                        }
                    }
                } else {
                    // Modo Por Cine (Por defecto)
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        items(schedule.cinemas) { cinema ->
                            CinemaSection(cinema, posterBitmaps)
                            Spacer(modifier = GlanceModifier.height(8.dp))
                        }
                    }
                }
            }
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

    @Composable
    private fun CinemaSection(cinema: CinemaSchedule, posterBitmaps: Map<String, Bitmap>) {
        val brandAccent = getCinemaAccent(cinema.cinemaName, cinema.cinemaId)

        Column(
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            // Título de la sección del Cine con tipografía sobria y punto acento
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "•",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = brandAccent
                    ),
                    modifier = GlanceModifier.padding(end = 4.dp)
                )

                Text(
                    text = cinema.cinemaName.uppercase(),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    )
                )

                Text(
                    text = "  ${cinema.location}",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = textTertiaryColor
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            if (cinema.status == "error" || cinema.movies.isEmpty()) {
                Text(
                    text = cinema.errorMessage ?: "Funciones no disponibles temporalmente.",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = textSecondaryColor
                    ),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(cardBgColor)
                        .padding(10.dp)
                )
            } else {
                cinema.movies.forEach { movie ->
                    MovieCard(movie, brandAccent, posterBitmaps[movie.coverImage])
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun MovieCard(movie: Movie, brandAccent: ColorProvider, posterBitmap: Bitmap?) {
        val metadata = buildList {
            movie.rating?.takeIf { it.isNotBlank() }?.let { add(it) }
            movie.durationMinutes?.let { add("${it} min") }
            movie.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBgColor)
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Columna 1 (Izquierda): Póster de la película (Bitmap real o Placeholder)
            Box(
                modifier = GlanceModifier
                    .width(44.dp)
                    .height(66.dp)
                    .background(chipBgColor)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (posterBitmap != null) {
                    Image(
                        provider = ImageProvider(posterBitmap),
                        contentDescription = movie.title,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "CINE",
                        style = TextStyle(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = textTertiaryColor,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // Columna 2 (Derecha): Información y Grid de Horarios
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
    private data class CinemaShowtimeGroup(
        val cinemaName: String,
        val location: String,
        val accent: ColorProvider,
        val showtimes: List<Showtime>
    )

    private data class MovieGroup(
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
    private fun MovieGroupedCard(movieGroup: MovieGroup, posterBitmaps: Map<String, Bitmap>) {
        val metadata = buildList {
            movieGroup.rating?.takeIf { it.isNotBlank() }?.let { add(it) }
            movieGroup.durationMinutes?.let { add("${it} min") }
            movieGroup.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")

        val posterBitmap = posterBitmaps[movieGroup.coverImage]

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBgColor)
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Póster a la izquierda
            Box(
                modifier = GlanceModifier
                    .width(44.dp)
                    .height(66.dp)
                    .background(chipBgColor)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (posterBitmap != null) {
                    Image(
                        provider = ImageProvider(posterBitmap),
                        contentDescription = movieGroup.title,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "CINE",
                        style = TextStyle(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = textTertiaryColor,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // Información y Horarios agrupados por Cine a la derecha
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
            ) {
                Text(
                    text = movieGroup.title,
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

                // Cines que tienen esta película
                movieGroup.cinemaGroups.forEach { cinemaGroup ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "•",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = cinemaGroup.accent
                            ),
                            modifier = GlanceModifier.padding(end = 4.dp)
                        )
                        Text(
                            text = "${cinemaGroup.cinemaName} (${cinemaGroup.location})",
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondaryColor
                            )
                        )
                    }

                    val chunkedShowtimes = cinemaGroup.showtimes.chunked(2)
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
