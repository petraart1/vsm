package ru.vsm.backend.gamification.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Ответ GET /api/gamification/profile/{playerId} — см. design/screens/profile.md.
 *
 * <p>Для нового игрока без завершённых прохождений возвращается с нулевыми счётчиками и
 * пустыми списками (не 404) — так фронт рисует "пустое" состояние профиля, а не ошибку.
 */
public record ProfileResponse(
        UUID playerId,
        String displayName,
        int totalScore,
        int scenariosCompleted,
        int totalScenariosAvailable,
        List<BlockProgressDto> blockProgress,
        List<AchievementDto> recentAchievements,
        Long leaderboardRank) {
}
