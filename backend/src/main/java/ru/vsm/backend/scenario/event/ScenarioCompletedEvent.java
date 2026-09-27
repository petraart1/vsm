package ru.vsm.backend.scenario.event;

import java.time.Instant;
import java.util.UUID;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;

/** Доменное событие: игрок завершил прохождение сценария (успешно, частично или провалом). */
public record ScenarioCompletedEvent(
        UUID userProgressId,
        UUID userId,
        UUID scenarioId,
        String scenarioCode,
        String scenarioBlock,
        ScenarioOutcome outcome,
        int loyaltyScore,
        int safetyScore,
        int choicesMade,
        boolean hadTimeout,
        boolean allRoleStepsFollowed,
        Instant startedAt,
        Instant completedAt,
        boolean examMode,
        boolean firstCompletion) {
}
