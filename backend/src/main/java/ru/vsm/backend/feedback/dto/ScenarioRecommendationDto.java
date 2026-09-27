package ru.vsm.backend.feedback.dto;

import java.util.UUID;

/** Один рекомендованный к прохождению сценарий (блок "какие сценарии пройти следующими" в */
public record ScenarioRecommendationDto(
        UUID scenarioId,
        String code,
        String title,
        String block,
        RecommendationReason reason) {
}
