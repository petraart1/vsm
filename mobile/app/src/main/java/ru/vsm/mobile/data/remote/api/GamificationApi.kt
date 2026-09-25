package ru.vsm.mobile.data.remote.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.vsm.mobile.data.remote.dto.AchievementDto
import ru.vsm.mobile.data.remote.dto.LeaderboardResponseDto
import ru.vsm.mobile.data.remote.dto.MarkAllReadResponseDto
import ru.vsm.mobile.data.remote.dto.NotificationDto
import ru.vsm.mobile.data.remote.dto.ProfileResponseDto

/** `ru.vsm.backend.gamification.web.{ProfileController,LeaderboardController,AchievementController,NotificationController}` */
interface GamificationApi {

    @GET("api/gamification/profile/{playerId}")
    suspend fun getProfile(@Path("playerId") playerId: String): ProfileResponseDto

    @GET("api/gamification/leaderboard")
    suspend fun getLeaderboard(
        @Query("limit") limit: Int,
        @Query("playerId") playerId: String?,
    ): LeaderboardResponseDto

    @GET("api/gamification/achievements")
    suspend fun getAchievements(@Query("playerId") playerId: String?): List<AchievementDto>

    @GET("api/gamification/notifications")
    suspend fun getNotifications(
        @Query("playerId") playerId: String,
        @Query("unreadOnly") unreadOnly: Boolean,
    ): List<NotificationDto>

    @POST("api/gamification/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): NotificationDto

    @POST("api/gamification/notifications/read-all")
    suspend fun markAllRead(@Query("playerId") playerId: String): MarkAllReadResponseDto
}
