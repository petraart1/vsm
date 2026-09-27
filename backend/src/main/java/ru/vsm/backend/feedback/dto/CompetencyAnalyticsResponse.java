package ru.vsm.backend.feedback.dto;

import java.util.List;
import java.util.UUID;

/** Аналитика компетенций игрока — агрегат по всем его завершённым прохождениям (см. */
public record CompetencyAnalyticsResponse(
        UUID playerId,
        int totalPlaythroughs,
        List<BlockCompetencyStatsDto> blockStats,
        List<RoleStepComplianceDto> roleStepCompliance,
        List<NormViolationDto> frequentNormViolations,
        List<String> weakCompetencies,
        List<ScenarioRecommendationDto> recommendations) {
}
