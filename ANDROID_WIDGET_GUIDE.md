# 📱 Guía de Implementación: Widget Android para Cartelera de Cines

Esta guía contiene la arquitectura, modelos de datos en Kotlin, componentes de interfaz y lógica de sincronización con **Jetpack Glance** y **WorkManager** para construir el widget de Android en su propio repositorio, consumiendo la API de este backend.

---

## 🏗️ 1. Arquitectura y Stack Tecnológico

```text
┌─────────────────────────────────────────────────────────────┐
│                       ANDROID CLIENT                        │
│                                                             │
│   ┌─────────────────────────────────────────────────────┐   │
│   │                 Jetpack Glance UI                   │   │
│   │  - Header (Fecha, Selector de Cine)                 │   │
│   │  - Películas y Chips de Horarios                    │   │
│   │  - Badge de Asientos (🟢 319 libres)                │   │
│   │  - Clic en horario -> Abrir compra en navegador     │   │
│   └──────────────────────────▲──────────────────────────┘   │
│                              │                              │
│   ┌──────────────────────────┴──────────────────────────┐   │
│   │             WorkManager Periodic Worker             │   │
│   │  (Sincroniza cada 30-60 min en segundo plano)       │   │
│   └──────────────────────────▲──────────────────────────┘   │
│                              │                              │
│   ┌──────────────────────────┴──────────────────────────┐   │
│   │                Retrofit / OkHttp                    │   │
│   │  GET /api/movies?date=YYYY-MM-DD                    │   │
│   └──────────────────────────▲──────────────────────────┘   │
└──────────────────────────────┼──────────────────────────────┘
                               │ HTTPS / JSON
                               ▼
                ┌─────────────────────────────┐
                │   Cine Widget Backend API   │
                └─────────────────────────────┘
```

### Tecnologías Recomendadas
* **Lenguaje:** Kotlin (Coroutines + Flow).
* **UI del Widget:** Jetpack Glance (`androidx.glance:glance-appwidget`, `androidx.glance:glance-material3`).
* **Sincronización en Background:** AndroidX WorkManager (`androidx.work:work-runtime-ktx`).
* **Red:** Retrofit 2 + Gson / Kotlinx Serialization.
* **Almacenamiento Local de Estado:** Jetpack DataStore / Room.

---

## 📦 2. Dependencias Gradle (`app/build.gradle.kts`)

```kotlin
dependencies {
    // Jetpack Glance (Widgets con sintaxis Compose)
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // WorkManager para tareas en segundo plano
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Retrofit & Serialización
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

---

## 🔌 3. Contrato de la API y Modelos Kotlin (DTOs)

El widget consume exclusivamente el endpoint unificado:
`GET /api/movies` (o `GET /api/movies?date=YYYY-MM-DD`)

### Modelos de Datos

```kotlin
package com.cinewidget.data.model

import com.google.gson.annotations.SerializedName

data class UnifiedScheduleResponse(
    @SerializedName("date") val date: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("cinemas") val cinemas: List<CinemaSchedule>
)

data class CinemaSchedule(
    @SerializedName("cinema_id") val cinemaId: String,
    @SerializedName("cinema_name") val cinemaName: String,
    @SerializedName("location") val location: String,
    @SerializedName("date") val date: String,
    @SerializedName("status") val status: String, // "ok" | "error"
    @SerializedName("error_message") val errorMessage: String?,
    @SerializedName("cached") val cached: Boolean,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("movies") val movies: List<Movie>
)

data class Movie(
    @SerializedName("title") val title: String,
    @SerializedName("slug") val slug: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("duration_minutes") val durationMinutes: Int?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("cover_image") val coverImage: String?,
    @SerializedName("showtimes") val showtimes: List<Showtime>
)

data class Showtime(
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("time") val time: String, // Formato "15:15"
    @SerializedName("language") val language: String?, // "DOB", "SUB", etc.
    @SerializedName("screen_types") val screenTypes: List<String>, // ["2D", "XD"]
    @SerializedName("seat_types") val seatTypes: List<String>,
    @SerializedName("seats_available") val seatsAvailable: Int?, // null si no está disponible
    @SerializedName("allocated_seating") val allocatedSeating: Boolean,
    @SerializedName("is_midnight") val isMidnight: Boolean,
    @SerializedName("booking_url") val bookingUrl: String?
)
```

---

## 🌐 4. Servicio Retrofit

```kotlin
package com.cinewidget.data.api

import com.cinewidget.data.model.UnifiedScheduleResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface CinemaApiService {
    @GET("api/movies")
    suspend fun getUnifiedSchedule(
        @Query("date") date: String? = null,
        @Query("refresh") refresh: Boolean = false
    ): UnifiedScheduleResponse
}
```

---

## 🎨 5. Componente Jetpack Glance (`CinemaWidget.kt`)

```kotlin
package com.cinewidget.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cinewidget.data.model.Movie
import com.cinewidget.data.model.Showtime

class CinemaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // En una implementación completa, se lee del repositorio/DataStore local
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(12.dp)
        ) {
            // Header del Widget
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎬 Cartelera de Cines",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onBackground
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Lista de Películas
            // LazyColumn { items(movies) { movie -> MovieItem(movie) } }
        }
    }

    @Composable
    private fun ShowtimeChip(showtime: Showtime) {
        val seatsText = when {
            showtime.seatsAvailable != null && showtime.seatsAvailable > 50 -> "🟢 ${showtime.seatsAvailable}"
            showtime.seatsAvailable != null && showtime.seatsAvailable > 0 -> "🟡 ${showtime.seatsAvailable}"
            showtime.seatsAvailable != null -> "🔴 Agotado"
            else -> ""
        }

        val formatText = (showtime.screenTypes + listOfNotNull(showtime.language)).joinToString(" · ")
        val intent = showtime.bookingUrl?.let { url ->
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        Row(
            modifier = GlanceModifier
                .padding(4.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(if (intent != null) actionStartActivity(intent) else actionStartActivity(Intent()))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${showtime.time} $formatText $seatsText",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
            )
        }
    }
}
```

---

## ⚙️ 6. Sincronización en Segundo Plano con WorkManager

```kotlin
package com.cinewidget.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cinewidget.widget.CinemaWidget

class ScheduleUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // 1. Consultar API Retrofit
            // 2. Guardar en DataStore / Base de datos local
            // 3. Notificar a Glance para refrescar la vista
            CinemaWidget().updateAll(context)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

---

## 📋 7. Registro en `AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application>
        <receiver
            android:name=".widget.CinemaWidgetReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/cinema_widget_info" />
        </receiver>
    </application>
</manifest>
```

---

## 💡 8. Checklist de Validación del Widget

- [ ] El widget muestra la cartelera en modo claro y oscuro respetando el tema del sistema.
- [ ] Al pulsar sobre el horario de una película, se abre el enlace directo de compra en el navegador predeterminado.
- [ ] La disponibilidad de asientos muestra badges visuales con color (`🟢`, `🟡`, `🔴`).
- [ ] Si Cine Colombia o Cinemark reporta `status: "error"`, el widget muestra el cine disponible sin colapsar.
- [ ] WorkManager actualiza los datos periódicamente cada 30-60 minutos de forma silenciosa y eficiente en batería.
