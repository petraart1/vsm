package ru.vsm.mobile.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Path
import ru.vsm.mobile.data.remote.dto.CompetencyAnalyticsResponseDto
import ru.vsm.mobile.data.remote.dto.DebriefResponseDto

/** `ru.vsm.backend.feedback.web.{DebriefController,CompetencyAnalyticsController}` */
interface FeedbackApi {

    @GET("api/feedback/debrief/{userProgressId}")
    suspend fun getDebrief(@Path("userProgressId") userProgressId: String): DebriefResponseDto

    @GET("api/feedback/competencies/{playerId}")
    suspend fun getCompetencies(@Path("playerId") playerId: String): CompetencyAnalyticsResponseDto
}
