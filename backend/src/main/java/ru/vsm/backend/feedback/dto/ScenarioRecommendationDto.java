package ru.vsm.backend.feedback.dto;

import java.util.UUID;

/**
 * Один рекомендованный к прохождению сценарий (блок "какие сценарии пройти следующими" в
 * {@link CompetencyAnalyticsResponse}).
 *
 * @param scenarioId id сценария
 * @param code       код сценария
 * @param title      заголовок сценария
 * @param block      блок датасета
 * @param reason     почему рекомендован — см. {@link RecommendationReason}
 */
public record ScenarioRecommendationDto(
        UUID scenarioId,
        String code,
        String title,
        String block,
        RecommendationReason reason) {
}
