package ru.vsm.backend.feedback.dto;

import java.util.List;
import java.util.UUID;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;

/** Разбор одного прохождения сценария (см. {@code design/screens/debrief.md}). Собирается на */
public record DebriefResponse(
        UUID userProgressId,
        UUID scenarioId,
        String scenarioCode,
        String scenarioTitle,
        String scenarioBlock,
        ProgressStatus progressStatus,
        ScenarioOutcome outcome,
        String verdict,
        boolean interrupted,
        int finalLoyaltyScore,
        int finalSafetyScore,
        List<DebriefStepDto> timeline,
        KeyMomentDto keyMoment,
        String summary,
        List<String> normReferences) {
}
