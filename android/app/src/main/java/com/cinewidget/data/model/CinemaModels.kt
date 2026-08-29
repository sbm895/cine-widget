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
    @SerializedName("time") val time: String, // "15:15"
    @SerializedName("language") val language: String?, // "DOB", "SUB", etc.
    @SerializedName("screen_types") val screenTypes: List<String> = emptyList(), // ["2D", "XD"]
    @SerializedName("seat_types") val seatTypes: List<String> = emptyList(),
    @SerializedName("seats_available") val seatsAvailable: Int?,
    @SerializedName("allocated_seating") val allocatedSeating: Boolean = true,
    @SerializedName("is_midnight") val isMidnight: Boolean = false,
    @SerializedName("booking_url") val bookingUrl: String?
)
