package ru.vsm.backend.scenario.web.dto;

import java.util.UUID;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;

/**
 * Результат применения выбора: теперь раскрываются дельты и новые значения шкал.
 * {@code nextNode} — null, если прохождение завершилось этим выбором (в т.ч. когда у выбора
 * {@code target == null} — конец сразу после выбора, без отдельного терминального узла).
 * {@code finalOutcome} заполнен только при {@code status == COMPLETED}.
 */
public record ChoiceAppliedResponse(
        UUID progressId,
        UUID appliedChoiceId,
        String appliedChoiceCode,
        boolean wasTimeout,
        int loyaltyDelta,
        int safetyDelta,
        int loyaltyScore,
        int safetyScore,
        ProgressStatus status,
        ScenarioOutcome finalOutcome,
        NodeStateResponse nextNode) {
}
