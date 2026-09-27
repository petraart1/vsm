package ru.vsm.mobile.data.repository

import ru.vsm.mobile.data.mapper.toDomain
import ru.vsm.mobile.data.mapper.toDto
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.GamificationApi
import ru.vsm.mobile.data.remote.dto.ShowcaseRequestDto
import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.model.Challenge
import ru.vsm.mobile.domain.model.CustomAward
import ru.vsm.mobile.domain.model.Leaderboard
import ru.vsm.mobile.domain.model.Notification
import ru.vsm.mobile.domain.model.Profile
import ru.vsm.mobile.domain.model.Showcase
import ru.vsm.mobile.domain.model.ShowcaseItem
import ru.vsm.mobile.domain.model.Team
import ru.vsm.mobile.domain.model.TeamLeaderboardEntry
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

    override suspend fun getTeamLeaderboard(): Result<List<TeamLeaderboardEntry>> = safeApiCall.call {
        api.getTeamLeaderboard().map { it.toDomain() }
    }

    override suspend fun getAchievements(playerId: String?): Result<List<Achievement>> = safeApiCall.call {
        api.getAchievements(playerId).map { it.toDomain() }
    }

    override suspend fun getCustomAwards(playerId: String?): Result<List<CustomAward>> = safeApiCall.call {
        api.getCustomAwards(playerId).map { it.toDomain() }
    }

    override suspend fun getChallenges(playerId: String?): Result<List<Challenge>> = safeApiCall.call {
        api.getChallenges(playerId).map { it.toDomain() }
    }

    override suspend fun putShowcase(playerId: String, finish: String, items: List<ShowcaseItem>): Result<Showcase> = safeApiCall.call {
        api.putShowcase(ShowcaseRequestDto(finish = finish, items = items.map { it.toDto() }), playerId).toDomain()
    }

    override suspend fun getShowcase(publicId: String): Result<Showcase> = safeApiCall.call {
        api.getShowcase(publicId).toDomain()
    }

    override suspend fun getTeams(): Result<List<Team>> = safeApiCall.call {
        api.listTeams().map { it.toDomain() }
    }

    override suspend fun joinTeam(teamId: String, playerId: String): Result<Team> = safeApiCall.call {
        api.joinTeam(teamId, playerId).toDomain()
    }

    override suspend fun getNotifications(playerId: String, unreadOnly: Boolean): Result<List<Notification>> = safeApiCall.call {
        api.getNotifications(playerId, unreadOnly).map { it.toDomain() }
    }

    override suspend fun markRead(notificationId: String, playerId: String): Result<Notification> = safeApiCall.call {
        api.markRead(notificationId, playerId).toDomain()
    }

    override suspend fun markAllRead(playerId: String): Result<Int> = safeApiCall.call {
        api.markAllRead(playerId).markedCount
    }
}
