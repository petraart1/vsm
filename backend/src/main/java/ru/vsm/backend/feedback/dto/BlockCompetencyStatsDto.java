package ru.vsm.backend.feedback.dto;

/** Агрегат по одному блоку ситуаций (boarding/medical/safety/...) за все завершённые */
public record BlockCompetencyStatsDto(
        String block,
        int playthroughs,
        double avgLoyaltyScore,
        double avgSafetyScore,
        double successRate,
        double failureRate,
        boolean weak) {
}
