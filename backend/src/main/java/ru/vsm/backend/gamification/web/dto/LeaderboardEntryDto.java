package ru.vsm.backend.gamification.web.dto;

/**
 * Строка публичного лидербода — намеренно без {@code playerId} (см. находку CRITICAL в аудите
 * безопасности: реальный {@code playerId} нельзя раскрывать анонимно, он же принимается как
 * самодостаточная личность игрока на пишущих игровых эндпоинтах). {@code publicId} —
 * {@code ru.vsm.backend.auth.security.PlayerPublicIdService}, стабильный, но необратимый к
 * исходному id. {@code me} — эта строка принадлежит текущему запрашивающему (см.
 * {@code ru.vsm.backend.auth.security.PlayerAccessGuard}).
 */
public record LeaderboardEntryDto(
        long rank,
        String publicId,
        String displayName,
        int totalScore,
        int scenariosCompleted,
        boolean me,
        int level,
        String levelTitle) {
}
