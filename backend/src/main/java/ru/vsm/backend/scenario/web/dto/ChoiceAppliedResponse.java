package ru.vsm.backend.scenario.web.dto;

import java.util.UUID;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;

/**
 * Результат применения выбора: раскрываются дельты и новые значения шкал — кроме как в режиме
 * экзамена (см. ниже). {@code nextNode} — null, если прохождение завершилось этим выбором (в т.ч.
 * когда у выбора {@code target == null} — конец сразу после выбора, без отдельного терминального
 * узла). {@code finalOutcome} заполнен только при {@code status == COMPLETED}.
 *
 * <p>{@code loyaltyDelta} — уже с модификатором класса вагона ({@code carClass}, см. javadoc
 * {@link CarClass}), применённым до клампинга шкалы на {@code [0, 100]} — фактически применённая
 * дельта, честная для разбора прохождения (см. javadoc {@code ScenarioPlayService.clampScale}
 * про тот же принцип для клампинга). {@code safetyDelta} модификатору класса не подвержен.
 *
 * <p><b>Режим экзамена</b>: {@code loyaltyDelta}/{@code safetyDelta}/{@code loyaltyScore}/
 * {@code safetyScore} — {@code null}, если прохождение, к которому относится этот выбор, отмечено
 * {@code UserProgress.examMode} (см. {@code ScenarioPlayService.applyResolvedChoice}) — экзамен не
 * должен подсказывать игроку качество решения по ходу прохождения. Навигационные поля
 * ({@code status}/{@code finalOutcome}/{@code nextNode}) при этом заполняются как обычно — экзамен
 * прячет только оценочный сигнал, не саму навигацию по графу.
 */
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
