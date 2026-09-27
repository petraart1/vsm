package ru.vsm.backend.gamification.web.dto;

import java.util.List;
import java.util.UUID;

/** Ответ GET /api/gamification/profile/{playerId} — см. design/screens/profile.md. */
public record ProfileResponse(
        UUID playerId,
        String displayName,
        int totalScore,
        int scenariosCompleted,
        int totalScenariosAvailable,
        List<BlockProgressDto> blockProgress,
        List<AchievementDto> recentAchievements,
        Long leaderboardRank,
        int level,
        String levelTitle,
        int levelProgress,
        Integer pointsToNextLevel) {
}
