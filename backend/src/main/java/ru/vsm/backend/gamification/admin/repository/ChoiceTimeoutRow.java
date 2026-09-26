package ru.vsm.backend.gamification.admin.repository;

import java.util.UUID;

/** JPQL-проекция {@link AdminChoiceHistoryStatsRepository#aggregateTimeoutsByScenario()}. */
public record ChoiceTimeoutRow(
        UUID scenarioId,
        Long totalChoices,
        Long timeoutChoices) {
}
