package ru.vsm.mobile.domain.fake

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.model.BlockProgress
import ru.vsm.mobile.domain.model.Leaderboard
import ru.vsm.mobile.domain.model.LeaderboardEntry
import ru.vsm.mobile.domain.model.Notification
import ru.vsm.mobile.domain.model.Profile
import ru.vsm.mobile.domain.repository.GamificationRepository

/** Фейковая реализация для UI-слоя до готовности сетевого data-слоя. Уведомления мутируются в памяти. */
class FakeGamificationRepository : GamificationRepository {

    private val achievementsCatalog = listOf(
        Achievement("first-scenario", "Первый рейс", "Пройдите первый сценарий", "onboarding", earned = true, earnedAt = Instant.parse("2026-09-20T10:00:00Z").toString()),
        Achievement("safety-master", "Мастер безопасности", "10 прохождений без потерь шкалы безопасности", "safety", earned = false, earnedAt = null),
    )

    private val notifications = mutableListOf(
        Notification(
            id = UUID.randomUUID().toString(),
            type = "achievement",
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
            ),
        )
    }

    override suspend fun getLeaderboard(limit: Int, playerId: String?): Result<Leaderboard> {
        delay(200)
        val top = (1..minOf(limit, 5)).map { rank ->
            LeaderboardEntry(
                rank = rank.toLong(),
                playerId = UUID.randomUUID().toString(),
                displayName = "Проводник $rank",
                totalScore = 500 - rank * 20,
                scenariosCompleted = 20 - rank,
            )
        }
        val me = playerId?.let {
            LeaderboardEntry(rank = 5L, playerId = it, displayName = "Проводник (вы)", totalScore = 120, scenariosCompleted = 2)
        }
        return Result.success(Leaderboard(top, me))
    }

    override suspend fun getAchievements(playerId: String?): Result<List<Achievement>> {
        delay(150)
        return Result.success(
            if (playerId == null) achievementsCatalog.map { it.copy(earned = false, earnedAt = null) } else achievementsCatalog,
        )
    }

    override suspend fun getNotifications(playerId: String, unreadOnly: Boolean): Result<List<Notification>> {
        delay(150)
        return Result.success(notifications.filter { !unreadOnly || it.unread })
    }

    override suspend fun markRead(notificationId: String): Result<Notification> {
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
