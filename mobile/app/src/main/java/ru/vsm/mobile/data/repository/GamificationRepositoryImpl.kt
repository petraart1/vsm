package ru.vsm.mobile.data.repository

import ru.vsm.mobile.data.mapper.toDomain
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.GamificationApi
import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.model.Leaderboard
import ru.vsm.mobile.domain.model.Notification
import ru.vsm.mobile.domain.model.Profile
import ru.vsm.mobile.domain.repository.GamificationRepository

class GamificationRepositoryImpl(
    private val api: GamificationApi,
    private val safeApiCall: SafeApiCall,
) : GamificationRepository {

    override suspend fun getProfile(playerId: String): Result<Profile> = safeApiCall.call {
        api.getProfile(playerId).toDomain()
    }

    override suspend fun getLeaderboard(limit: Int, playerId: String?): Result<Leaderboard> = safeApiCall.call {
        api.getLeaderboard(limit, playerId).toDomain()
    }

    override suspend fun getAchievements(playerId: String?): Result<List<Achievement>> = safeApiCall.call {
        api.getAchievements(playerId).map { it.toDomain() }
    }

    override suspend fun getNotifications(playerId: String, unreadOnly: Boolean): Result<List<Notification>> = safeApiCall.call {
        api.getNotifications(playerId, unreadOnly).map { it.toDomain() }
    }

    override suspend fun markRead(notificationId: String): Result<Notification> = safeApiCall.call {
        api.markRead(notificationId).toDomain()
    }

    override suspend fun markAllRead(playerId: String): Result<Int> = safeApiCall.call {
        api.markAllRead(playerId).markedCount
    }
}
