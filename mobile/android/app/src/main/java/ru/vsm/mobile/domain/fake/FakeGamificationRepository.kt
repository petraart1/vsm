package ru.vsm.mobile.domain.fake

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.model.BlockProgress
import ru.vsm.mobile.domain.model.Challenge
import ru.vsm.mobile.domain.model.CustomAward
import ru.vsm.mobile.domain.model.Leaderboard
import ru.vsm.mobile.domain.model.LeaderboardEntry
import ru.vsm.mobile.domain.model.Notification
import ru.vsm.mobile.domain.model.NotificationType
import ru.vsm.mobile.domain.model.Profile
import ru.vsm.mobile.domain.model.Showcase
import ru.vsm.mobile.domain.model.ShowcaseItem
import ru.vsm.mobile.domain.model.Team
import ru.vsm.mobile.domain.model.TeamLeaderboardEntry
import ru.vsm.mobile.domain.repository.GamificationRepository

/** Фейковая реализация для UI-слоя до готовности сетевого data-слоя. Уведомления/витрина мутируются в памяти. */
class FakeGamificationRepository : GamificationRepository {

    private val achievementsCatalog = listOf(
        Achievement("first-scenario", "Первый рейс", "Пройдите первый сценарий", "onboarding", earned = true, earnedAt = Instant.parse("2026-09-20T10:00:00Z").toString()),
        Achievement("safety-master", "Мастер безопасности", "10 прохождений без потерь шкалы безопасности", "safety", earned = false, earnedAt = null),
    )

    private val customAwardsCatalog = listOf(
        CustomAward(
            id = UUID.randomUUID().toString(),
            code = "mentor-choice",
            title = "Выбор наставника",
            description = "Награда от наставника за образцовую смену",
            shape = "hexagon",
            glyph = "star",
            verifiedOnly = true,
            earned = false,
            earnedAt = null,
        ),
    )

    private val teamsCatalog = listOf(
        Team(id = UUID.randomUUID().toString(), code = "msk-depot-1", name = "Депо Москва-1", depot = "Москва", memberCount = 12),
        Team(id = UUID.randomUUID().toString(), code = "spb-depot-1", name = "Депо Санкт-Петербург-1", depot = "Санкт-Петербург", memberCount = 9),
    )

    private var showcase = Showcase(finish = "metal", items = emptyList())

    private val notifications = mutableListOf(
        Notification(
            id = UUID.randomUUID().toString(),
            type = NotificationType.ACHIEVEMENT_UNLOCKED,
            title = "Новая ачивка",
            body = "Вы получили «Первый рейс»",
            createdAt = Instant.parse("2026-09-20T10:00:05Z").toString(),
            readAt = null,
        ),
    )

    override suspend fun getProfile(playerId: String): Result<Profile> {
        delay(200)
        return Result.success(
            Profile(
                playerId = playerId,
                displayName = "Проводник",
                totalScore = 120,
                scenariosCompleted = 2,
                totalScenariosAvailable = 51,
                blockProgress = listOf(
                    BlockProgress("boarding", scenariosCompleted = 1, loyaltyPoints = 2, safetyPoints = 5),
                    BlockProgress("medical", scenariosCompleted = 1, loyaltyPoints = -5, safetyPoints = -10),
                ),
                recentAchievements = achievementsCatalog.filter { it.earned },
                leaderboardRank = 5L,
                level = 2,
                levelTitle = "Проводник 2 класса",
                levelProgress = 40,
                pointsToNextLevel = 180,
            ),
        )
    }

    override suspend fun getLeaderboard(limit: Int, playerId: String?): Result<Leaderboard> {
        delay(200)
        val top = (1..minOf(limit, 5)).map { rank ->
            LeaderboardEntry(
                rank = rank.toLong(),
                publicId = UUID.randomUUID().toString().replace("-", "").take(16),
                displayName = "Проводник $rank",
                totalScore = 500 - rank * 20,
                scenariosCompleted = 20 - rank,
                me = false,
                level = 3,
                levelTitle = "Проводник 1 класса",
            )
        }
        val me = playerId?.let {
            LeaderboardEntry(
                rank = 5L,
                publicId = it.replace("-", "").take(16),
                displayName = "Проводник (вы)",
                totalScore = 120,
                scenariosCompleted = 2,
                me = true,
                level = 2,
                levelTitle = "Проводник 2 класса",
            )
        }
        return Result.success(Leaderboard(top, me))
    }

    override suspend fun getTeamLeaderboard(): Result<List<TeamLeaderboardEntry>> {
        delay(150)
        return Result.success(
            teamsCatalog.mapIndexed { index, team ->
                TeamLeaderboardEntry(
                    rank = (index + 1).toLong(),
                    teamId = team.id,
                    code = team.code,
                    name = team.name,
                    depot = team.depot,
                    memberCount = team.memberCount,
                    totalScore = 1000L - index * 100,
                    averageScore = 90.0 - index * 5,
                    totalPlaythroughs = 40L - index * 3,
                    averageSafety = 8.0 - index * 0.5,
                )
            },
        )
    }

    override suspend fun getAchievements(playerId: String?): Result<List<Achievement>> {
        delay(150)
        return Result.success(
            if (playerId == null) achievementsCatalog.map { it.copy(earned = false, earnedAt = null) } else achievementsCatalog,
        )
    }

    override suspend fun getCustomAwards(playerId: String?): Result<List<CustomAward>> {
        delay(150)
        return Result.success(
            if (playerId == null) customAwardsCatalog.map { it.copy(earned = false, earnedAt = null) } else customAwardsCatalog,
        )
    }

    override suspend fun getChallenges(playerId: String?): Result<List<Challenge>> {
        delay(150)
        return Result.success(
            listOf(
                Challenge(
                    code = "september-no-failure",
                    title = "Без потерь в сентябре",
                    description = "Пройдите 10 сценариев блока «посадка» без исхода \"неудача\"",
                    goalType = "BLOCK_SCENARIOS_NO_FAILURE",
                    targetBlock = "boarding",
                    targetCount = 10,
                    safetyThreshold = null,
                    rewardPoints = 50,
                    startsAt = Instant.parse("2026-09-01T00:00:00Z").toString(),
                    endsAt = Instant.parse("2026-09-30T23:59:59Z").toString(),
                    current = if (playerId != null) 3 else 0,
                    completed = false,
                    completedAt = null,
                ),
            ),
        )
    }

    override suspend fun putShowcase(playerId: String, finish: String, items: List<ShowcaseItem>): Result<Showcase> {
        delay(150)
        showcase = Showcase(finish = finish, items = items.take(6))
        return Result.success(showcase)
    }

    override suspend fun getShowcase(publicId: String): Result<Showcase> {
        delay(150)
        return Result.success(showcase)
    }

    override suspend fun getTeams(): Result<List<Team>> {
        delay(150)
        return Result.success(teamsCatalog)
    }

    override suspend fun joinTeam(teamId: String, playerId: String): Result<Team> {
        delay(150)
        val team = teamsCatalog.find { it.id == teamId }
            ?: return Result.failure(DomainError.Api(404, "team_not_found", "Команда '$teamId' не найдена"))
        return Result.success(team)
    }

    override suspend fun getNotifications(playerId: String, unreadOnly: Boolean): Result<List<Notification>> {
        delay(150)
        return Result.success(notifications.filter { !unreadOnly || it.unread })
    }

    override suspend fun markRead(notificationId: String, playerId: String): Result<Notification> {
        delay(100)
        val index = notifications.indexOfFirst { it.id == notificationId }
        if (index == -1) {
            return Result.failure(DomainError.Api(404, "notification_not_found", "Уведомление '$notificationId' не найдено"))
        }
        val updated = notifications[index].copy(readAt = Instant.now().toString())
        notifications[index] = updated
        return Result.success(updated)
    }

    override suspend fun markAllRead(playerId: String): Result<Int> {
        delay(100)
        var count = 0
        val now = Instant.now().toString()
        for (i in notifications.indices) {
            if (notifications[i].unread) {
                notifications[i] = notifications[i].copy(readAt = now)
                count++
            }
        }
        return Result.success(count)
    }
}
