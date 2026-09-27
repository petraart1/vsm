package ru.vsm.mobile.data.mapper

import ru.vsm.mobile.data.remote.dto.AchievementDto
import ru.vsm.mobile.data.remote.dto.BlockProgressDto
import ru.vsm.mobile.data.remote.dto.LeaderboardEntryDto
import ru.vsm.mobile.data.remote.dto.LeaderboardResponseDto
import ru.vsm.mobile.data.remote.dto.NotificationDto as NotificationDtoRemote
import ru.vsm.mobile.data.remote.dto.ProfileResponseDto
import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.model.BlockProgress
import ru.vsm.mobile.domain.model.Leaderboard
import ru.vsm.mobile.domain.model.LeaderboardEntry
import ru.vsm.mobile.domain.model.Notification
import ru.vsm.mobile.domain.model.Profile

fun BlockProgressDto.toDomain(): BlockProgress = BlockProgress(
    block = block,
    scenariosCompleted = scenariosCompleted,
    loyaltyPoints = loyaltyPoints,
    safetyPoints = safetyPoints,
)

fun AchievementDto.toDomain(): Achievement = Achievement(
    code = code,
    title = title,
    description = description,
    category = category,
    earned = earned,
    earnedAt = earnedAt,
)

fun ProfileResponseDto.toDomain(): Profile = Profile(
    playerId = playerId,
    displayName = displayName,
    totalScore = totalScore,
    scenariosCompleted = scenariosCompleted,
    totalScenariosAvailable = totalScenariosAvailable,
    blockProgress = blockProgress.map { it.toDomain() },
    recentAchievements = recentAchievements.map { it.toDomain() },
    leaderboardRank = leaderboardRank,
)

fun LeaderboardEntryDto.toDomain(): LeaderboardEntry = LeaderboardEntry(
    rank = rank,
    playerId = playerId,
    displayName = displayName,
    totalScore = totalScore,
    scenariosCompleted = scenariosCompleted,
)

fun LeaderboardResponseDto.toDomain(): Leaderboard = Leaderboard(
    top = top.map { it.toDomain() },
    me = me?.toDomain(),
)

fun NotificationDtoRemote.toDomain(): Notification = Notification(
    id = id,
    type = type,
    title = title,
    body = body,
    createdAt = createdAt,
    readAt = readAt,
)
