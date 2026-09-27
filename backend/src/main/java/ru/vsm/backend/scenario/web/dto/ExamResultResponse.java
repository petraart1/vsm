package ru.vsm.backend.scenario.web.dto;

import java.util.List;
import ru.vsm.backend.scenario.domain.ExamGrade;

/** Итог завершённого экзамена (см. {@link ExamGrade} — пороги оценки в javadoc enum'а). */
public record ExamResultResponse(
        double avgLoyaltyScore,
        double avgSafetyScore,
        double successRate,
        ExamGrade grade,
        List<String> weakBlocks) {
}
