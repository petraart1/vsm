package ru.vsm.mobile.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.vsm.mobile.data.remote.dto.ChoiceAppliedDto
import ru.vsm.mobile.data.remote.dto.ProgressStateDto
import ru.vsm.mobile.data.remote.dto.ScenarioSummaryDto

/** `ru.vsm.backend.scenario.web.{ScenarioCatalogController,ScenarioPlayController}` */
interface ScenarioApi {

    /** Идентификация игрока в API прохождения — заголовок с UUID на каждый запрос (без Spring Security в MVP). */
    companion object {
        const val PLAYER_ID_HEADER = "X-Player-Id"
    }

    @GET("api/scenarios")
    suspend fun listScenarios(@Query("block") block: String?): List<ScenarioSummaryDto>

    @POST("api/scenarios/{scenarioId}/progress")
    suspend fun start(
        @Path("scenarioId") scenarioId: String,
        @Header(PLAYER_ID_HEADER) playerId: String,
    ): ProgressStateDto

    @GET("api/scenarios/progress/{progressId}")
    suspend fun getProgress(
        @Path("progressId") progressId: String,
        @Header(PLAYER_ID_HEADER) playerId: String,
    ): ProgressStateDto

    @POST("api/scenarios/progress/{progressId}/choices/{choiceId}")
    suspend fun choose(
        @Path("progressId") progressId: String,
        @Path("choiceId") choiceId: String,
        @Header(PLAYER_ID_HEADER) playerId: String,
    ): ChoiceAppliedDto

    @POST("api/scenarios/progress/{progressId}/timeout")
    suspend fun timeout(
        @Path("progressId") progressId: String,
        @Header(PLAYER_ID_HEADER) playerId: String,
    ): ChoiceAppliedDto
}
