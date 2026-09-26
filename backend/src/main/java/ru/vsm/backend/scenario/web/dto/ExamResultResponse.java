package ru.vsm.backend.scenario.web.dto;

import java.util.List;
import ru.vsm.backend.scenario.domain.ExamGrade;

/**
 * Итог завершённого экзамена (см. {@link ExamGrade} — пороги оценки в javadoc enum'а).
 *
 * @param weakBlocks блоки датасета, где сценарий экзамена не завершился {@code SUCCESS}, от
 *                   худшего к менее слабому — ориентир, что подтянуть перед пересдачей
 */
public record ExamResultResponse(
        double avgLoyaltyScore,
        double avgSafetyScore,
        double successRate,
        ExamGrade grade,
        List<String> weakBlocks) {
}
