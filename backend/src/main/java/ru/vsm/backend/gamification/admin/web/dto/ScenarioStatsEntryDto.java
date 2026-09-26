package ru.vsm.backend.gamification.admin.web.dto;

import java.util.UUID;

/**
 * Одна строка {@code GET /api/admin/stats/scenarios} — только сценарии хотя бы с одним
 * прохождением (список отсортирован по возрастанию {@code successRate}, самые
 * "проваливаемые" — первыми).
 *
 * <p>{@code successRate} — доля {@code COMPLETED} прохождений с {@code finalOutcome = SUCCESS}.
 * {@code timeoutRate} — доля выборов, применённых автоматически по истечении таймера, среди
 * всех сделанных выборов по этому сценарию.
 */
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
