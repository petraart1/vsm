package ru.vsm.backend.scenario.web.dto;

import java.util.UUID;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;

/**
 * Один пункт экзамена в порядке прохождения. {@code userProgressId} — {@code null}, пока игрок ещё
 * не начал этот пункт; {@code completed}/{@code outcome}/{@code loyaltyScore}/{@code safetyScore} —
 * заполняются только после того, как соответствующее прохождение завершилось.
 */
public record ExamScenarioResponse(
        int sortOrder,
        UUID scenarioId,
        String scenarioCode,
        String block,
        String title,
        boolean flagship,
        UUID userProgressId,
        boolean completed,
        ScenarioOutcome outcome,
        Integer loyaltyScore,
        Integer safetyScore) {
}
