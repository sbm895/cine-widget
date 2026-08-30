package com.cinewidget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cinewidget.data.api.RetrofitClient
import com.cinewidget.data.model.CinemaSchedule
import com.cinewidget.data.model.Movie
import com.cinewidget.data.model.Showtime
import com.cinewidget.data.model.UnifiedScheduleResponse
import com.cinewidget.worker.ScheduleUpdateWorker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupPeriodicSync(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF141214)
                ) {
                    AppScreen(this)
                }
            }
        }
    }

    private fun setupPeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<ScheduleUpdateWorker>(
            30, TimeUnit.MINUTES,
            10, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "cinema_schedule_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }
}

// Paleta de colores minimalista
private val WidgetBg = Color(0xFF1E1A1D)
private val CardBg = Color(0xFF282328)
private val TextPrimary = Color(0xFFF3EDF1)
private val TextSecondary = Color(0xFFA89FA6)
private val TextTertiary = Color(0xFF7E757D)
private val ChipBg = Color(0xFF332D34)
private val ActiveTabBg = Color(0xFF4A414B)
private val CinemarkAccent = Color(0xFFE50914)
private val CinecoAccent = Color(0xFF1E88E5)
private val RoyalAccent = Color(0xFFE5A00D)

@Composable
fun AppScreen(context: Context) {
    val prefs = remember { context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    var backendUrl by remember {
        mutableStateOf(prefs.getString("backend_url", "") ?: "")
    }

    var isSyncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf("by_cinema") } // "by_cinema" | "by_movie"

    var schedule by remember {
        mutableStateOf<UnifiedScheduleResponse?>(
            prefs.getString("last_schedule_json", null)?.let {
                try {
                    Gson().fromJson(it, UnifiedScheduleResponse::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        )
    }

    fun doSync(forceRefresh: Boolean = false) {
        val url = backendUrl.trim()
        if (url.isBlank()) {
            Toast.makeText(context, "Por favor ingresa una URL válida", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().putString("backend_url", url).commit()
        isSyncing = true
        syncError = null

        coroutineScope.launch {
            try {
                val apiService = RetrofitClient.createService(url)
                val response = withContext(Dispatchers.IO) {
                    apiService.getUnifiedSchedule(refresh = forceRefresh)
                }

                val json = Gson().toJson(response)
                prefs.edit().putString("last_schedule_json", json).commit()
                schedule = response
                isSyncing = false

                // Disparar worker para refrescar el widget también
                val request = OneTimeWorkRequestBuilder<ScheduleUpdateWorker>()
                    .setInputData(workDataOf("force_refresh" to forceRefresh))
                    .build()
                WorkManager.getInstance(context).enqueue(request)
                val msg = if (forceRefresh) "Cartelera forzada y actualizada en vivo" else "Cartelera sincronizada desde caché rápida"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                syncError = e.localizedMessage ?: "Error al conectar con el backend"
                isSyncing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Encabezado y configuración
        Text(
            text = "🎬 Cine Widget",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = backendUrl,
            onValueChange = { backendUrl = it },
            label = { Text("URL de API (/api/movies)") },
            placeholder = { Text("https://tu-api.a.run.app") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = CinecoAccent,
                unfocusedBorderColor = TextTertiary,
                focusedLabelColor = TextPrimary,
                unfocusedLabelColor = TextSecondary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Dos botones de sincronización separados
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botón 1: Rápido (Caché SWR - 1 ms)
            Button(
                onClick = { if (!isSyncing) doSync(forceRefresh = false) },
                enabled = !isSyncing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSyncing) TextTertiary else CinecoAccent
                )
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("⚡ Rápido (1 ms)", fontSize = 12.sp)
                }
            }

            // Botón 2: Forzar Scraping en Vivo (refresh = true)
            OutlinedButton(
                onClick = { if (!isSyncing) doSync(forceRefresh = true) },
                enabled = !isSyncing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = RoyalAccent
                )
            ) {
                Text("🔄 Forzar En Vivo", fontSize = 12.sp)
            }
        }

        if (syncError != null) {
            Text(
                text = "⚠️ Error: $syncError",
                color = CinemarkAccent,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Contenedor de Vista Previa del Widget
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = WidgetBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header de la Vista Previa
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Vista Previa del Widget",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = schedule?.date ?: "Barranquilla y Soledad",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    // Botón actualizar simulado
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSyncing) ChipBg else CardBg)
                            .clickable(enabled = !isSyncing) { doSync() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isSyncing) "Sincronizando..." else "Actualizar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSyncing) TextTertiary else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selector de modo Por Cine / Por Película
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChipBg)
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewMode == "by_cinema") ActiveTabBg else ChipBg)
                            .clickable { viewMode = "by_cinema" }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Por Cine",
                            fontSize = 12.sp,
                            fontWeight = if (viewMode == "by_cinema") FontWeight.Bold else FontWeight.Normal,
                            color = if (viewMode == "by_cinema") TextPrimary else TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewMode == "by_movie") ActiveTabBg else ChipBg)
                            .clickable { viewMode = "by_movie" }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Por Película",
                            fontSize = 12.sp,
                            fontWeight = if (viewMode == "by_movie") FontWeight.Bold else FontWeight.Normal,
                            color = if (viewMode == "by_movie") TextPrimary else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Contenido de la Cartelera
                if (isSyncing && schedule == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CinecoAccent)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Consultando /api/movies...", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                } else if (schedule == null || schedule!!.cinemas.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ingresa la URL y toca Sincronizar para cargar la cartelera.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val currentCinemas = schedule!!.cinemas
                    if (viewMode == "by_movie") {
                        val grouped = groupScheduleByMovieApp(currentCinemas).take(18)
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(grouped) { movieGroup ->
                                AppMovieGroupCard(movieGroup, context)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    } else {
                        var expandedCinemasApp by remember {
                            mutableStateOf(setOf("cinemark-gran-plaza-del-sol"))
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            var totalRendered = 0
                            val maxLimit = 18

                            currentCinemas.forEach { cinema ->
                                val accent = getCinemaAccentApp(cinema.cinemaName, cinema.cinemaId)
                                val isExpanded = expandedCinemasApp.contains(cinema.cinemaId)

                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CardBg)
                                            .clickable {
                                                expandedCinemasApp = if (isExpanded) {
                                                    expandedCinemasApp - cinema.cinemaId
                                                } else {
                                                    expandedCinemasApp + cinema.cinemaId
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isExpanded) "▼  " else "▶  ",
                                            color = accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = cinema.cinemaName.uppercase(),
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "  ${cinema.location}",
                                            color = TextTertiary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (cinema.movies.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(ChipBg)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${cinema.movies.size} pelis",
                                                    color = TextTertiary,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (isExpanded) {
                                    if (cinema.status == "error" || cinema.movies.isEmpty()) {
                                        item {
                                            Text(
                                                text = cinema.errorMessage ?: "Sin funciones disponibles.",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(CardBg)
                                                    .padding(10.dp)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                    } else {
                                        for (movie in cinema.movies) {
                                            if (totalRendered >= maxLimit) {
                                                item {
                                                    Text(
                                                        text = "⚡ Mostrando 18 películas activas. Colapsa un cine para explorar los demás con fluidez.",
                                                        color = TextTertiary,
                                                        fontSize = 10.sp,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 8.dp)
                                                    )
                                                }
                                                break
                                            }
                                            item {
                                                AppMovieCard(movie, accent, context)
                                                Spacer(modifier = Modifier.height(6.dp))
                                            }
                                            totalRendered++
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getCinemaAccentApp(cinemaName: String, cinemaId: String): Color {
    val lowerName = cinemaName.lowercase()
    val lowerId = cinemaId.lowercase()
    return when {
        lowerName.contains("cinemark") || lowerId.contains("cinemark") -> CinemarkAccent
        lowerName.contains("royal") || lowerId.contains("royal") -> RoyalAccent
        else -> CinecoAccent
    }
}

@Composable
private fun AppMovieCard(movie: Movie, brandAccent: Color, context: Context) {
    val metadata = buildList {
        movie.rating?.takeIf { it.isNotBlank() }?.let { add(it) }
        movie.durationMinutes?.let { add("${it} min") }
        movie.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Póster
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(66.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ChipBg),
            contentAlignment = Alignment.Center
        ) {
            if (!movie.coverImage.isNullOrBlank()) {
                val imageRequest = ImageRequest.Builder(LocalContext.current)
                    .data(movie.coverImage)
                    .crossfade(true)
                    .setHeader("Referer", "https://www.cinecolombia.com/")
                    .build()

                AsyncImage(
                    model = imageRequest,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("CINE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(movie.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (metadata.isNotEmpty()) {
                Text(metadata, fontSize = 10.sp, color = TextTertiary, modifier = Modifier.padding(vertical = 2.dp))
            }

            val chunkedShowtimes = movie.showtimes.chunked(2)
            chunkedShowtimes.forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowItems.forEach { showtime ->
                        Box(modifier = Modifier.weight(1f)) {
                            AppShowtimeChip(showtime, context)
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class AppCinemaShowtimeGroup(
    val cinemaName: String,
    val location: String,
    val accent: Color,
    val showtimes: List<Showtime>
)

private data class AppMovieGroup(
    val title: String,
    val rating: String?,
    val durationMinutes: Int?,
    val genre: String?,
    val coverImage: String?,
    val cinemaGroups: List<AppCinemaShowtimeGroup>
)

private fun groupScheduleByMovieApp(cinemas: List<CinemaSchedule>): List<AppMovieGroup> {
    val movieMap = linkedMapOf<String, AppMovieGroup>()

    cinemas.forEach { cinema ->
        val accent = getCinemaAccentApp(cinema.cinemaName, cinema.cinemaId)
        cinema.movies.forEach { movie ->
            val normalizedKey = movie.title.trim().lowercase().replace(".", "").replace(":", "")
            val existing = movieMap[normalizedKey]
            val currentGroup = AppCinemaShowtimeGroup(
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
                movieMap[normalizedKey] = AppMovieGroup(
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
private fun AppMovieGroupCard(movieGroup: AppMovieGroup, context: Context) {
    val metadata = buildList {
        movieGroup.rating?.takeIf { it.isNotBlank() }?.let { add(it) }
        movieGroup.durationMinutes?.let { add("${it} min") }
        movieGroup.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(66.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ChipBg),
            contentAlignment = Alignment.Center
        ) {
            if (!movieGroup.coverImage.isNullOrBlank()) {
                val imageRequest = ImageRequest.Builder(LocalContext.current)
                    .data(movieGroup.coverImage)
                    .crossfade(true)
                    .setHeader("Referer", "https://www.cinecolombia.com/")
                    .build()

                AsyncImage(
                    model = imageRequest,
                    contentDescription = movieGroup.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("CINE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(movieGroup.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (metadata.isNotEmpty()) {
                Text(metadata, fontSize = 10.sp, color = TextTertiary, modifier = Modifier.padding(vertical = 2.dp))
            }

            movieGroup.cinemaGroups.forEach { cinemaGroup ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("•", color = cinemaGroup.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${cinemaGroup.cinemaName} (${cinemaGroup.location})", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                val chunkedShowtimes = cinemaGroup.showtimes.chunked(2)
                chunkedShowtimes.forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowItems.forEach { showtime ->
                            Box(modifier = Modifier.weight(1f)) {
                                AppShowtimeChip(showtime, context)
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppShowtimeChip(showtime: Showtime, context: Context) {
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(ChipBg)
            .clickable {
                if (!showtime.bookingUrl.isNullOrBlank()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(showtime.bookingUrl))
                    context.startActivity(intent)
                }
            }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = chipText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
    }
}

