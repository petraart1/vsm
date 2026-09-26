package ru.vsm.backend.gamification.admin.web.dto;

/**
 * {@code GET /api/admin/stats/overview} — сводка по всем игрокам и прохождениям.
 *
 * <p>{@code successRate}/{@code partialRate}/{@code failureRate} — доли от
 * {@code completedPlaythroughs} (0, если завершённых прохождений ещё нет).
 */
public record OverviewStatsResponse(
        long totalPlayers,
        long totalPlaythroughs,
        long completedPlaythroughs,
        long inProgressPlaythroughs,
        double avgLoyaltyScore,
        double avgSafetyScore,
        double successRate,
        double partialRate,
        double failureRate,
        long activePlayers24h,
        long activePlayers7d) {
}
