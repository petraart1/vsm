package ru.vsm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/** `ru.vsm.backend.gamification.web.dto.BlockProgressDto` */
@Serializable
data class BlockProgressDto(
    val block: String,
    val scenariosCompleted: Int,
    val loyaltyPoints: Int,
    val safetyPoints: Int,
)

/** `ru.vsm.backend.gamification.web.dto.AchievementDto` */
@Serializable
data class AchievementDto(
    val code: String,
    val title: String,
    val description: String,
    val category: String,
    val earned: Boolean,
    val earnedAt: String? = null,
)

/** `ru.vsm.backend.gamification.web.dto.ProfileResponse` */
@Serializable
data class ProfileResponseDto(
    val playerId: String,
    val displayName: String,
    val totalScore: Int,
    val scenariosCompleted: Int,
    val totalScenariosAvailable: Int,
    val blockProgress: List<BlockProgressDto> = emptyList(),
    val recentAchievements: List<AchievementDto> = emptyList(),
    val leaderboardRank: Long? = null,
    val level: Int,
    val levelTitle: String,
    val levelProgress: Int,
    val pointsToNextLevel: Int? = null,
)

/**
 * `ru.vsm.backend.gamification.web.dto.LeaderboardEntryDto` — намеренно без `playerId`
 * (см. javadoc на backend): [publicId] — стабильный, но необратимый публичный идентификатор.
 */
@Serializable
data class LeaderboardEntryDto(
    val rank: Long,
    val publicId: String,
    val displayName: String,
    val totalScore: Int,
    val scenariosCompleted: Int,
    val me: Boolean,
    val level: Int,
    val levelTitle: String,
)

/** `ru.vsm.backend.gamification.web.dto.LeaderboardResponse` */
@Serializable
data class LeaderboardResponseDto(
    val top: List<LeaderboardEntryDto> = emptyList(),
    val me: LeaderboardEntryDto? = null,
)

/** `ru.vsm.backend.gamification.web.dto.NotificationDto` */
@Serializable
data class NotificationDto(
    val id: String,
    val type: NotificationTypeDto = NotificationTypeDto.UNKNOWN,
    val title: String,
    val body: String,
    val createdAt: String,
    val readAt: String? = null,
)

/** `ru.vsm.backend.gamification.web.NotificationController.MarkAllReadResponse` */
@Serializable
data class MarkAllReadResponseDto(
    val markedCount: Int,
)

/** `ru.vsm.backend.gamification.challenge.web.dto.ChallengeDto` — челлендж месяца с прогрессом игрока. */
@Serializable
data class ChallengeDto(
    val code: String,
    val title: String,
    val description: String,
    val goalType: String,
    val targetBlock: String? = null,
    val targetCount: Int,
    val safetyThreshold: Int? = null,
    val rewardPoints: Int,
    val startsAt: String,
    val endsAt: String,
    val current: Int,
    val completed: Boolean,
    val completedAt: String? = null,
)

/** `ru.vsm.backend.gamification.award.web.dto.CustomAwardDto` — награда, выданная администратором вручную. */
@Serializable
data class CustomAwardDto(
    val id: String,
    val code: String,
    val title: String,
    val description: String,
    val shape: String,
    val glyph: String? = null,
    val verifiedOnly: Boolean,
    val earned: Boolean,
    val earnedAt: String? = null,
    val grantedCount: Long,
)

/** `ru.vsm.backend.gamification.team.web.dto.TeamDto` */
@Serializable
data class TeamDto(
    val id: String,
    val code: String,
    val name: String,
    val depot: String,
    val memberCount: Long,
)

/** `ru.vsm.backend.gamification.team.web.dto.TeamLeaderboardEntryDto` */
@Serializable
data class TeamLeaderboardEntryDto(
    val rank: Long,
    val teamId: String,
    val code: String,
    val name: String,
    val depot: String,
    val memberCount: Long,
    val totalScore: Long,
    val averageScore: Double,
    val totalPlaythroughs: Long,
    val averageSafety: Double,
)

/** `ru.vsm.backend.gamification.showcase.web.dto.ShowcaseItemDto` — одна награда в витрине игрока. */
@Serializable
data class ShowcaseItemDto(
    val id: String,
    val title: String,
    val shape: String,
    val glyph: String? = null,
    val text: String? = null,
)

/** Тело `PUT /api/gamification/showcase` — `ru.vsm.backend.gamification.showcase.web.dto.ShowcaseRequest`. */
@Serializable
data class ShowcaseRequestDto(
    val finish: String,
    val items: List<ShowcaseItemDto>,
)

/** `ru.vsm.backend.gamification.showcase.web.dto.ShowcaseResponse` */
@Serializable
data class ShowcaseResponseDto(
    val finish: String,
    val items: List<ShowcaseItemDto> = emptyList(),
)
