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
