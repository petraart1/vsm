package ru.vsm.backend.gamification.admin.web.dto;

/**
 * Одна строка {@code GET /api/admin/stats/blocks} — агрегат по блоку ситуаций
 * (boarding/baggage/safety/seating/comfort/catering/medical/lost_found/conflict/misc), только
 * блоки хотя бы с одним прохождением.
 */
public record BlockStatsEntryDto(
        String block,
        long totalPlaythroughs,
        long completedPlaythroughs,
        double successRate,
        double avgLoyaltyScore,
        double avgSafetyScore) {
}
