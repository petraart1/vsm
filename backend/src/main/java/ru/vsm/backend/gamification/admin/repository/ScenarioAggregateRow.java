package ru.vsm.backend.gamification.admin.repository;

import java.util.UUID;

/** JPQL-проекция {@link AdminProgressStatsRepository#aggregateByScenario()}, одна строка на сценарий. */
public record ScenarioAggregateRow(
        UUID scenarioId,
        Long totalPlaythroughs,
        Long completedCount,
        Long successCount,
        Double avgLoyalty,
        Double avgSafety) {
}
