package ru.vsm.backend.scenario.web.dto;

import java.util.UUID;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;

/** Результат применения выбора: раскрываются дельты и новые значения шкал — кроме как в режиме */
public record ChoiceAppliedResponse(
        UUID progressId,
        UUID appliedChoiceId,
        String appliedChoiceCode,
        boolean wasTimeout,
        CarClass carClass,
        Integer loyaltyDelta,
        Integer safetyDelta,
        Integer loyaltyScore,
        Integer safetyScore,
        ProgressStatus status,
        ScenarioOutcome finalOutcome,
        NodeStateResponse nextNode) {
}
