package ru.vsm.backend.gamification.web.dto;

/** Строка публичного лидербода — намеренно без {@code playerId} (см. находку CRITICAL в аудите */
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
