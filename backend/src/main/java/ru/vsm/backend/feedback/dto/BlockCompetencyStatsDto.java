package ru.vsm.backend.feedback.dto;

/**
 * Агрегат по одному блоку ситуаций (boarding/medical/safety/...) за все завершённые
 * прохождения игрока в этом блоке — один из срезов ответа
 * {@link CompetencyAnalyticsResponse}.
 *
 * @param block          ключ блока датасета
 * @param playthroughs   число завершённых прохождений сценариев этого блока
 * @param avgLoyaltyScore средняя итоговая шкала лояльности по прохождениям блока
 * @param avgSafetyScore  средняя итоговая шкала безопасности по прохождениям блока
 * @param successRate    доля прохождений с исходом SUCCESS (0..1)
 * @param failureRate    доля прохождений с исходом FAILURE (0..1)
 * @param weak           true, если блок попал в {@link CompetencyAnalyticsResponse#weakCompetencies()} —
 *                       правило см. в javadoc {@code CompetencyAnalyticsService}
 */
public record BlockCompetencyStatsDto(
        String block,
        int playthroughs,
        double avgLoyaltyScore,
        double avgSafetyScore,
        double successRate,
        double failureRate,
        boolean weak) {
}
