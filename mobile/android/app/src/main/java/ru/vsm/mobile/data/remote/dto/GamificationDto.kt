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
)

/** `ru.vsm.backend.gamification.web.dto.LeaderboardEntryDto` */
@Serializable
data class LeaderboardEntryDto(
    val rank: Long,
    val playerId: String,
    val displayName: String,
    val totalScore: Int,
    val scenariosCompleted: Int,
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
    val type: String,
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
