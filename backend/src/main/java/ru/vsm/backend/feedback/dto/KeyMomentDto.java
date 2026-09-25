package ru.vsm.backend.feedback.dto;

/**
 * Блок "Что можно было сделать иначе" (см. {@code design/screens/debrief.md}): ключевая развилка,
 * где игрок принял неоптимальное решение — фактический выбор против лучшей альтернативы
 * в том же узле. {@code null} в {@link ru.vsm.backend.feedback.dto.DebriefResponse#keyMoment()},
 * если игрок на каждой развилке выбирал вариант не хуже лучшего (идеальное прохождение).
 *
 * @param sequenceIndex          порядковый номер шага таймлайна, где произошла развилка
 * @param nodeText               реплика/ситуация узла развилки
 * @param chosenChoiceText       вариант, который выбрал игрок
 * @param chosenLoyaltyDelta     дельта лояльности выбранного варианта
 * @param chosenSafetyDelta      дельта безопасности выбранного варианта
 * @param betterChoiceText       лучшая альтернатива в том же узле
 * @param betterLoyaltyDelta     дельта лояльности лучшей альтернативы
 * @param betterSafetyDelta      дельта безопасности лучшей альтернативы
 * @param adviceText             обучающий текст 2-4 предложения: почему альтернатива сильнее
 */
public record KeyMomentDto(
        int sequenceIndex,
        String nodeText,
        String chosenChoiceText,
        int chosenLoyaltyDelta,
        int chosenSafetyDelta,
        String betterChoiceText,
        int betterLoyaltyDelta,
        int betterSafetyDelta,
        String adviceText) {
}
