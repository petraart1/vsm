package ru.vsm.backend.scenario.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.domain.ExamStatus;

/** Состояние экзамена: упорядоченный список пунктов ({@link #scenarios}, см. {@link ExamScenarioResponse}), */
public record ExamResponse(
        UUID examId,
        UUID playerId,
        CarClass carClass,
        ExamStatus status,
        int size,
        int currentIndex,
        Instant startedAt,
        Instant finishedAt,
        List<ExamScenarioResponse> scenarios,
        ExamResultResponse result) {
}
