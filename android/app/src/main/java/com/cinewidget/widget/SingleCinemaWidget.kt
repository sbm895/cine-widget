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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.update
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

class SingleCinemaWidget : GlanceAppWidget() {

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
        val selectedIndex = prefs.getInt("single_cinema_index", 0)
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
                SingleCinemaContent(schedule, selectedIndex)
            }
        }
    }

    @Composable
    private fun SingleCinemaContent(
        schedule: UnifiedScheduleResponse?,
        selectedIndex: Int
    ) {
        val context = LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBgColor)
                .padding(12.dp)
        ) {
            if (schedule == null || schedule.cinemas.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin cartelera cargada.\nToca para abrir la app.",
                        style = TextStyle(
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = textSecondaryColor
                        )
                    )
                }
                return@Column
            }

            val cinemas = schedule.cinemas
            val validIndex = selectedIndex.coerceIn(0, cinemas.size - 1)
            val currentCinema = cinemas[validIndex]
            val brandAccent = getCinemaAccent(currentCinema.cinemaName, currentCinema.cinemaId)

            // Selector de Cine Paginado: [ ? ] [ Cine Colombia Buenavista ] [ ? ]
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(cardBgColor)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Anterior
                Text(
                    text = " ? ",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = brandAccent
                    ),
                    modifier = GlanceModifier
                        .clickable(
                            actionRunCallback<SwitchCinemaActionCallback>(
                                SwitchCinemaActionCallback.createParameters(
                                    if (validIndex > 0) validIndex - 1 else cinemas.size - 1
                                )
                            )
                        )
                        .padding(4.dp)
                )

                // Nombre de Cine Central (Abre la app al tocarlo)
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java))),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentCinema.cinemaName.uppercase(),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimaryColor,
                            textAlign = TextAlign.Center
                        )
                    )
                    Text(
                        text = "${currentCinema.location} · ${currentCinema.movies.size} pelis",
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = textTertiaryColor,
                            textAlign = TextAlign.Center
                        )
                    )
                }

                // Botón Siguiente
                Text(
                    text = " ? ",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = brandAccent
                    ),
                    modifier = GlanceModifier
                        .clickable(
                            actionRunCallback<SwitchCinemaActionCallback>(
                                SwitchCinemaActionCallback.createParameters(
                                    if (validIndex < cinemas.size - 1) validIndex + 1 else 0
                                )
                            )
                        )
                        .padding(4.dp)
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Lista plana de películas para el cine seleccionado
            if (currentCinema.movies.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentCinema.errorMessage ?: "Sin funciones disponibles.",
                        style = TextStyle(fontSize = 11.sp, color = textSecondaryColor)
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(currentCinema.movies) { movie ->
                        MovieRow(movie, brandAccent)
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun MovieRow(movie: Movie, brandAccent: ColorProvider) {
        val metadata = buildList {
            movie.rating?.takeIf { it.isNotBlank() }?.let { add(it) }
            movie.durationMinutes?.let { add("${it} min") }
            movie.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBgColor)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = GlanceModifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(brandAccent)
            ) {}

            Spacer(modifier = GlanceModifier.width(8.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = movie.title,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor
                    )
                )
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata,
                        style = TextStyle(fontSize = 9.sp, color = textTertiaryColor),
                        modifier = GlanceModifier.padding(bottom = 4.dp)
                    )
                }

                val chunked = movie.showtimes.chunked(2)
                chunked.forEach { rowItems ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowItems.forEach { showtime ->
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .padding(horizontal = 2.dp)
                            ) {
                                SingleShowtimeChip(showtime)
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
    private fun SingleShowtimeChip(showtime: Showtime) {
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

class SwitchCinemaActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val targetIndex = parameters[TargetIndexKey] ?: 0
        val prefs = context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("single_cinema_index", targetIndex).commit()
        SingleCinemaWidget().update(context, glanceId)
    }

    companion object {
        val TargetIndexKey = ActionParameters.Key<Int>("target_cinema_index")

        fun createParameters(index: Int): ActionParameters {
            return actionParametersOf(TargetIndexKey to index)
        }
    }
}
