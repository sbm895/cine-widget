package com.cinewidget.data.api

import com.cinewidget.data.model.CinemaSchedule
import com.cinewidget.data.model.UnifiedScheduleResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface CinemaApiService {
    @GET("api/movies")
    suspend fun getUnifiedSchedule(
        @Query("date") date: String? = null,
        @Query("refresh") refresh: Boolean = false
    ): UnifiedScheduleResponse

    @GET("api/cinemark")
    suspend fun getCinemarkSchedule(
        @Query("date") date: String? = null,
        @Query("refresh") refresh: Boolean = false
    ): CinemaSchedule

    @GET("api/cinecolombia")
    suspend fun getCineColombiaSchedule(
        @Query("theater") theater: String = "parque-alegra",
        @Query("date") date: String? = null,
        @Query("refresh") refresh: Boolean = false
    ): CinemaSchedule

    @GET("api/royalfilms")
    suspend fun getRoyalFilmsSchedule(
        @Query("theater") theater: String = "viva",
        @Query("date") date: String? = null,
        @Query("refresh") refresh: Boolean = false
    ): CinemaSchedule
}
