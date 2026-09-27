package ru.vsm.mobile.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.vsm.mobile.data.remote.dto.CarClassDto
import ru.vsm.mobile.data.remote.dto.ExamResponseDto
import ru.vsm.mobile.data.remote.dto.ProgressStateDto

/** `ru.vsm.backend.scenario.web.ExamController` */
interface ExamApi {

    @POST("api/exams")
    suspend fun create(
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerId: String,
        @Query("carClass") carClass: CarClassDto?,
        @Query("size") size: Int?,
    ): ExamResponseDto

    @GET("api/exams/{examId}")
    suspend fun get(
        @Path("examId") examId: String,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerId: String,
    ): ExamResponseDto

    @POST("api/exams/{examId}/current")
    suspend fun startCurrent(
        @Path("examId") examId: String,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerId: String,
    ): ProgressStateDto
}
