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
    private val chipBorderColor = ColorProvider(Color(0xFF453D46))      // Borde / acento sutil chip
    private val cinemarkAccent = ColorProvider(Color(0xFFE50914))       // Acento Cinemark
    private val cinecoAccent = ColorProvider(Color(0xFF1E88E5))         // Acento Cine Colombia

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        val isSyncing = prefs.getBoolean("is_syncing", false)
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

        // Descargar pósters en segundo plano con Coil (redimensionados para RemoteViews)
        val posterBitmaps = withContext(Dispatchers.IO) {
            val map = mutableMapOf<String, Bitmap>()
            if (schedule != null) {
                val imageLoader = ImageLoader(context)
                schedule.cinemas.flatMap { it.movies }.forEach { movie ->
                    val url = movie.coverImage
                    if (!url.isNullOrBlank() && !map.containsKey(url)) {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .size(120, 180) // Tamaño miniatura óptimo para widgets
                                .scale(Scale.FIT)
                                .allowHardware(false) // Necesario para RemoteViews/Glance
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
                WidgetContent(schedule, isSyncing, posterBitmaps)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        schedule: UnifiedScheduleResponse?,
        isSyncing: Boolean,
        posterBitmaps: Map<String, Bitmap>
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBgColor)
                .padding(14.dp)
        ) {
            // Header Minimalista (Similar a "Tokyo / Mostly cloudy" en la referencia)
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

            Spacer(modifier = GlanceModifier.height(10.dp))

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
                // Usamos LazyColumn para scroll vertical fluido de todos los cines
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

    @Composable
    private fun CinemaSection(cinema: CinemaSchedule, posterBitmaps: Map<String, Bitmap>) {
        val isCinemark = cinema.cinemaName.contains("cinemark", ignoreCase = true) ||
                cinema.cinemaId.contains("cinemark", ignoreCase = true)

        val brandAccent = if (isCinemark) cinemarkAccent else cinecoAccent

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
                    MovieCard(movie, isCinemark, posterBitmaps[movie.coverImage])
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun MovieCard(movie: Movie, isCinemark: Boolean, posterBitmap: Bitmap?) {
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

            // Columna 2 (Derecha): Información y Grid de Horarios estilo pronóstico
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
                                ShowtimeChip(showtime, isCinemark)
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
