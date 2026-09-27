package ru.vsm.mobile.data.mapper

import ru.vsm.mobile.data.remote.dto.AchievementDto
import ru.vsm.mobile.data.remote.dto.BlockProgressDto
import ru.vsm.mobile.data.remote.dto.ChallengeDto
import ru.vsm.mobile.data.remote.dto.CustomAwardDto
import ru.vsm.mobile.data.remote.dto.LeaderboardEntryDto
import ru.vsm.mobile.data.remote.dto.LeaderboardResponseDto
import ru.vsm.mobile.data.remote.dto.NotificationDto as NotificationDtoRemote
import ru.vsm.mobile.data.remote.dto.NotificationTypeDto
import ru.vsm.mobile.data.remote.dto.ProfileResponseDto
import ru.vsm.mobile.data.remote.dto.ShowcaseItemDto
import ru.vsm.mobile.data.remote.dto.ShowcaseResponseDto
import ru.vsm.mobile.data.remote.dto.TeamDto
import ru.vsm.mobile.data.remote.dto.TeamLeaderboardEntryDto
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
    level = level,
    levelTitle = levelTitle,
    levelProgress = levelProgress,
    pointsToNextLevel = pointsToNextLevel,
)

fun LeaderboardEntryDto.toDomain(): LeaderboardEntry = LeaderboardEntry(
    rank = rank,
    publicId = publicId,
    displayName = displayName,
    totalScore = totalScore,
    scenariosCompleted = scenariosCompleted,
    me = me,
    level = level,
    levelTitle = levelTitle,
)

fun LeaderboardResponseDto.toDomain(): Leaderboard = Leaderboard(
    top = top.map { it.toDomain() },
    me = me?.toDomain(),
)

fun TeamLeaderboardEntryDto.toDomain(): TeamLeaderboardEntry = TeamLeaderboardEntry(
    rank = rank,
    teamId = teamId,
    code = code,
    name = name,
    depot = depot,
    memberCount = memberCount,
    totalScore = totalScore,
    averageScore = averageScore,
    totalPlaythroughs = totalPlaythroughs,
    averageSafety = averageSafety,
)

fun CustomAwardDto.toDomain(): CustomAward = CustomAward(
    id = id,
    code = code,
    title = title,
    description = description,
    shape = shape,
    glyph = glyph,
    verifiedOnly = verifiedOnly,
    earned = earned,
    earnedAt = earnedAt,
)

fun ChallengeDto.toDomain(): Challenge = Challenge(
    code = code,
    title = title,
    description = description,
    goalType = goalType,
    targetBlock = targetBlock,
    targetCount = targetCount,
    safetyThreshold = safetyThreshold,
    rewardPoints = rewardPoints,
    startsAt = startsAt,
    endsAt = endsAt,
    current = current,
    completed = completed,
    completedAt = completedAt,
)

fun ShowcaseItemDto.toDomain(): ShowcaseItem = ShowcaseItem(
    id = id,
    title = title,
    shape = shape,
    glyph = glyph,
    text = text,
)

fun ShowcaseItem.toDto(): ShowcaseItemDto = ShowcaseItemDto(
    id = id,
    title = title,
    shape = shape,
    glyph = glyph,
    text = text,
)

fun ShowcaseResponseDto.toDomain(): Showcase = Showcase(
    finish = finish,
    items = items.map { it.toDomain() },
)

fun TeamDto.toDomain(): Team = Team(
    id = id,
    code = code,
    name = name,
    depot = depot,
    memberCount = memberCount,
)

fun NotificationTypeDto.toDomain(): NotificationType = when (this) {
    NotificationTypeDto.ACHIEVEMENT_UNLOCKED -> NotificationType.ACHIEVEMENT_UNLOCKED
    NotificationTypeDto.NEW_PERSONAL_BEST -> NotificationType.NEW_PERSONAL_BEST
    NotificationTypeDto.LEADERBOARD_RANK_UP -> NotificationType.LEADERBOARD_RANK_UP
    NotificationTypeDto.RECOMMENDED_SCENARIO -> NotificationType.RECOMMENDED_SCENARIO
    NotificationTypeDto.CHALLENGE_COMPLETED -> NotificationType.CHALLENGE_COMPLETED
    NotificationTypeDto.TEAM_RANK_UP -> NotificationType.TEAM_RANK_UP
    NotificationTypeDto.EXAM_COMPLETED -> NotificationType.EXAM_COMPLETED
    NotificationTypeDto.NEW_SCENARIO -> NotificationType.NEW_SCENARIO
    NotificationTypeDto.NEW_CHALLENGE -> NotificationType.NEW_CHALLENGE
    NotificationTypeDto.POINTS_EXPIRING -> NotificationType.POINTS_EXPIRING
    NotificationTypeDto.POINTS_EXPIRED -> NotificationType.POINTS_EXPIRED
    NotificationTypeDto.LEVEL_UP -> NotificationType.LEVEL_UP
    NotificationTypeDto.UNKNOWN -> NotificationType.UNKNOWN
}

fun NotificationDtoRemote.toDomain(): Notification = Notification(
    id = id,
    type = type.toDomain(),
    title = title,
    body = body,
    createdAt = createdAt,
    readAt = readAt,
)
