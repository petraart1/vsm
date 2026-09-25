package ru.vsm.backend.feedback.dto;

import java.util.List;
import java.util.UUID;

/**
 * Аналитика компетенций игрока — агрегат по всем его завершённым прохождениям (см.
 * {@code ru.vsm.backend.feedback.service.CompetencyAnalyticsService}), в отличие от
 * {@link DebriefResponse}, который разбирает одно прохождение.
 *
 * <p>Игрок без завершённых прохождений получает этот же DTO с {@code totalPlaythroughs = 0} и
 * пустыми списками (кроме {@code recommendations} — см. javadoc сервиса), а не 404.
 *
 * @param playerId              id игрока
 * @param totalPlaythroughs     число завершённых прохождений, вошедших в агрегат
 * @param blockStats            агрегат по каждому блоку ситуаций, где есть хотя бы одно прохождение
 * @param roleStepCompliance    как часто соблюдался/пропускался каждый из 4 шагов ролевой модели
 * @param frequentNormViolations самые частые нарушения норм (выборы с отрицательной дельтой
 *                              безопасности и заполненным normRef), по убыванию частоты
 * @param weakCompetencies      2-3 просевшие компетенции (коды блоков) — правило отбора см. в
 *                              javadoc {@code CompetencyAnalyticsService.findWeakCompetencies}
 * @param recommendations       какие сценарии пройти следующими (непройденные/проваленные в
 *                              просевших компетенциях, либо просто непройденные, если просевших
 *                              компетенций нет)
 */
public record CompetencyAnalyticsResponse(
        UUID playerId,
        int totalPlaythroughs,
        List<BlockCompetencyStatsDto> blockStats,
        List<RoleStepComplianceDto> roleStepCompliance,
        List<NormViolationDto> frequentNormViolations,
        List<String> weakCompetencies,
        List<ScenarioRecommendationDto> recommendations) {
}
