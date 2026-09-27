package ru.vsm.mobile.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import ru.vsm.mobile.data.remote.dto.AchievementDto
import ru.vsm.mobile.data.remote.dto.ChallengeDto
import ru.vsm.mobile.data.remote.dto.CustomAwardDto
import ru.vsm.mobile.data.remote.dto.LeaderboardResponseDto
import ru.vsm.mobile.data.remote.dto.MarkAllReadResponseDto
import ru.vsm.mobile.data.remote.dto.NotificationDto
import ru.vsm.mobile.data.remote.dto.ProfileResponseDto
import ru.vsm.mobile.data.remote.dto.ShowcaseRequestDto
import ru.vsm.mobile.data.remote.dto.ShowcaseResponseDto
import ru.vsm.mobile.data.remote.dto.TeamDto
import ru.vsm.mobile.data.remote.dto.TeamLeaderboardEntryDto

/**
 * `ru.vsm.backend.gamification.web.{ProfileController,LeaderboardController,AchievementController,NotificationController}`,
 * `ru.vsm.backend.gamification.challenge.web.ChallengeController`,
 * `ru.vsm.backend.gamification.award.web.CustomAwardController`,
 * `ru.vsm.backend.gamification.showcase.web.ShowcaseController`,
 * `ru.vsm.backend.gamification.team.web.{TeamController,TeamLeaderboardController}`.
 *
 * Эндпоинты, идентифицирующие игрока по `playerId` в пути/запросе, дополнительно шлют его же в
 * заголовке [ScenarioApi.PLAYER_ID_HEADER] — backend (`PlayerAccessGuard`) сверяет владельца
 * именно по заголовку или JWT, а не по query/path значению самому по себе (иначе можно было бы
 * подставить чужой `playerId` и прочитать его данные, см. аудит безопасности в README backend).
 */
interface GamificationApi {

    @GET("api/gamification/profile/{playerId}")
    suspend fun getProfile(
        @Path("playerId") playerId: String,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerIdHeader: String = playerId,
    ): ProfileResponseDto

    /** `me` в ответе определяется личностью самого запроса — [playerId] не query-параметр, только заголовок. */
    @GET("api/gamification/leaderboard")
    suspend fun getLeaderboard(
        @Query("limit") limit: Int,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerId: String?,
    ): LeaderboardResponseDto

    @GET("api/gamification/leaderboard/teams")
    suspend fun getTeamLeaderboard(): List<TeamLeaderboardEntryDto>

    @GET("api/gamification/achievements")
    suspend fun getAchievements(
        @Query("playerId") playerId: String?,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerIdHeader: String? = playerId,
    ): List<AchievementDto>

    @GET("api/gamification/custom-awards")
    suspend fun getCustomAwards(
        @Query("playerId") playerId: String?,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerIdHeader: String? = playerId,
    ): List<CustomAwardDto>

    /** Без [playerId] — каталог с нулевым прогрессом, доступа не требует (см. javadoc на backend). */
    @GET("api/gamification/challenges")
    suspend fun getChallenges(
        @Query("playerId") playerId: String?,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerIdHeader: String? = playerId,
    ): List<ChallengeDto>

    @GET("api/gamification/notifications")
    suspend fun getNotifications(
        @Query("playerId") playerId: String,
        @Query("unreadOnly") unreadOnly: Boolean,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerIdHeader: String = playerId,
    ): List<NotificationDto>

    @POST("api/gamification/notifications/{id}/read")
    suspend fun markRead(
        @Path("id") id: String,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerId: String,
    ): NotificationDto

    @POST("api/gamification/notifications/read-all")
    suspend fun markAllRead(
        @Query("playerId") playerId: String,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerIdHeader: String = playerId,
    ): MarkAllReadResponseDto

    /** Заменяет витрину текущего игрока целиком (не более 6 наград) — владелец только по заголовку/JWT. */
    @PUT("api/gamification/showcase")
    suspend fun putShowcase(
        @Body request: ShowcaseRequestDto,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerId: String,
    ): ShowcaseResponseDto

    /** Публичная витрина коллеги по `publicId` из лидерборда — без идентификации запрашивающего. */
    @GET("api/gamification/showcase/{publicId}")
    suspend fun getShowcase(@Path("publicId") publicId: String): ShowcaseResponseDto

    @GET("api/gamification/teams")
    suspend fun listTeams(): List<TeamDto>

    /** Повторный вызов с другим `id` — смена команды (backend это разрешает). */
    @POST("api/gamification/teams/{id}/join")
    suspend fun joinTeam(
        @Path("id") teamId: String,
        @Header(ScenarioApi.PLAYER_ID_HEADER) playerId: String,
    ): TeamDto
}
