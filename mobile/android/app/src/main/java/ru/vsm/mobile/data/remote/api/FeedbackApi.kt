package ru.vsm.mobile.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import ru.vsm.mobile.data.remote.dto.CompetencyAnalyticsResponseDto
import ru.vsm.mobile.data.remote.dto.DebriefResponseDto

/** `ru.vsm.backend.feedback.web.{DebriefController,CompetencyAnalyticsController}` */
interface FeedbackApi {

    /**
     * `playerId` — владелец прохождения (совпадать с ним должен заголовок [ScenarioApi.PLAYER_ID_HEADER]
     * либо `Authorization: Bearer`, см. `PlayerAccessGuard` на backend); без одного из них ответ `403`.
     */
    @GET("api/feedback/debrief/{userProgressId}")
    suspend fun getDebrief(
        @Path("userProgressId") userProgressId: String,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerId: String,
    ): DebriefResponseDto

    @GET("api/feedback/competencies/{playerId}")
    suspend fun getCompetencies(
        @Path("playerId") playerId: String,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerIdHeader: String = playerId,
    ): CompetencyAnalyticsResponseDto
}
