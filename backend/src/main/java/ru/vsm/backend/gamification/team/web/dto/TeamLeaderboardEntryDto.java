package ru.vsm.backend.gamification.team.web.dto;

import java.util.UUID;

/**
 * Строка рейтинга команд — GET /api/gamification/leaderboard/teams.
 *
 * <p>{@code rank} назначается по {@code averageScore} по убыванию (см. javadoc
 * {@code TeamService#getLeaderboard} — почему средний, а не суммарный балл, определяет позицию).
 */
public record TeamLeaderboardEntryDto(
        long rank,
        UUID teamId,
        String code,
        String name,
        String depot,
        long memberCount,
        long totalScore,
        double averageScore,
        long totalPlaythroughs,
        double averageSafety) {
}
