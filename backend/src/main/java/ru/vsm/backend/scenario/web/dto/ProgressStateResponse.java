package ru.vsm.backend.scenario.web.dto;

import java.util.UUID;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.domain.ProgressStatus;

/** Состояние прохождения: id, шкалы, текущий узел (null, если прохождение уже завершено). */
public record ProgressStateResponse(
        UUID progressId,
        UUID scenarioId,
        String scenarioCode,
        ProgressStatus status,
        CarClass carClass,
        int loyaltyScore,
        int safetyScore,
        NodeStateResponse currentNode) {
}
