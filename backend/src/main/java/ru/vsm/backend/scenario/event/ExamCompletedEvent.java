package ru.vsm.backend.scenario.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.vsm.backend.scenario.domain.ExamGrade;

/** Доменное событие: игрок завершил экзамен целиком (все сценарии {@link ru.vsm.backend.scenario.domain.Exam} */
public record ExamCompletedEvent(
        UUID examId,
        UUID playerId,
        double avgLoyaltyScore,
        double avgSafetyScore,
        double successRate,
        ExamGrade grade,
        List<String> weakBlocks,
        Instant startedAt,
        Instant finishedAt) {
}
