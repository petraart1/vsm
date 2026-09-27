package ru.vsm.backend.gamification.admin.web.dto;

import java.util.UUID;

/** Одна строка {@code GET /api/admin/stats/scenarios} — только сценарии хотя бы с одним */
public record ScenarioStatsEntryDto(
        UUID scenarioId,
        String code,
        String title,
        String block,
        long totalPlaythroughs,
        long completedPlaythroughs,
        double successRate,
        double avgLoyaltyScore,
        double avgSafetyScore,
        double timeoutRate) {
}
