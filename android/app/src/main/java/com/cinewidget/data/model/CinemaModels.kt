package com.cinewidget.data.model

import com.google.gson.annotations.SerializedName

data class UnifiedScheduleResponse(
    @SerializedName("date") val date: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("cinemas") val cinemas: List<CinemaSchedule> = emptyList()
)

data class CinemaSchedule(
    @SerializedName("cinema_id") val cinemaId: String,
    @SerializedName("cinema_name") val cinemaName: String,
    @SerializedName("location") val location: String,
    @SerializedName("date") val date: String,
    @SerializedName("status") val status: String, // "ok" | "error"
    @SerializedName("error_message") val errorMessage: String? = null,
    @SerializedName("cached") val cached: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("movies") val movies: List<Movie> = emptyList()
)

data class Movie(
    @SerializedName("title") val title: String,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("duration_minutes") val durationMinutes: Int? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("cover_image") val coverImage: String? = null,
    @SerializedName("requires_proxy") val requiresProxy: Boolean = false,
    @SerializedName("showtimes") val showtimes: List<Showtime> = emptyList()
)

data class Showtime(
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("time") val time: String, // Formato "HH:MM" (ej: "15:40")
    @SerializedName("language") val language: String? = null, // "DOB", "SUB", "ESP"
    @SerializedName("screen_types") val screenTypes: List<String> = emptyList(), // ["2D", "3D", "XD", "MegaSala"]
    @SerializedName("seat_types") val seatTypes: List<String> = emptyList(), // ["GENERAL", "VIP"]
    @SerializedName("seats_available") val seatsAvailable: Int? = null, // NULO en Cineco, Número en Cinemark
    @SerializedName("allocated_seating") val allocatedSeating: Boolean = true,
    @SerializedName("is_midnight") val isMidnight: Boolean = false,
    @SerializedName("booking_url") val bookingUrl: String? = null
)
