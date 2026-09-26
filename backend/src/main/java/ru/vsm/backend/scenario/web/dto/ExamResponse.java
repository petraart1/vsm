package ru.vsm.backend.scenario.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.domain.ExamStatus;

/**
 * Состояние экзамена: упорядоченный список пунктов ({@link #scenarios}, см. {@link ExamScenarioResponse}),
 * {@link #currentIndex} — позиция первого не пройденного пункта. {@link #result} — {@code null}, пока
 * {@link #status} не {@link ExamStatus#COMPLETED}.
 */
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
