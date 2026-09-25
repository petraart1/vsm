package ru.vsm.backend.scenario.event;

import java.time.Instant;
import java.util.UUID;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;

/**
 * Доменное событие: игрок завершил прохождение сценария (успешно, частично или провалом).
 *
 * <p>Публикуется через {@link org.springframework.context.ApplicationEventPublisher#publishEvent(Object)}
 * (in-process, без Kafka) при переходе {@code UserProgress.status} в {@code COMPLETED}. Слушают:
 * <ul>
 *   <li><b>gamification</b> — начисление очков компетенций, ачивки, лидерборд;</li>
 *   <li><b>feedback</b> — обучающий разбор решений и аналитика пробелов, читает также
 *       {@code ScenarioChoiceHistory} по {@link #userProgressId} для деталей по шагам.</li>
 * </ul>
 *
 * <p>Контракт полей — синхронизировать при изменении сигнатуры между доменами.
 *
 * @param userProgressId id записи {@code user_progress}, ключ для чтения полной истории
 *                        выборов ({@code ScenarioChoiceHistory})
 * @param userId          id игрока (домен gamification, без FK в БД)
 * @param scenarioId      id сценария
 * @param scenarioCode    человекочитаемый код сценария (для логов/аналитики без join'а)
 * @param scenarioBlock   блок датасета (boarding/medical/...), для группировки в аналитике
 * @param outcome         итоговый исход прохождения
 * @param loyaltyScore    итоговое значение шкалы "лояльность пассажира" на момент завершения
 * @param safetyScore     итоговое значение шкалы "рейтинг безопасности" на момент завершения
 * @param choicesMade     сколько решений принял игрок за прохождение
 * @param hadTimeout      был ли хотя бы один выбор применён автоматически по таймеру
 * @param allRoleStepsFollowed true, если по совокупности всех выборов прохождения хотя бы раз
 *        встретился каждый из 4 шагов универсальной ролевой модели (признать/обозначить
 *        правило/предложить решение/заверить) — см. {@link ru.vsm.backend.scenario.domain.RoleStepFlags}.
 *        Считается по объединению флагов всех сделанных выборов, а не по одному выбору (один
 *        ответ обычно закрывает 1-2 шага, а не все 4 сразу). Источник для ачивки gamification
 *        "без пропуска шагов ролевой модели".
 * @param startedAt       момент начала прохождения
 * @param completedAt     момент завершения прохождения
 */
public record ScenarioCompletedEvent(
        UUID userProgressId,
        UUID userId,
        UUID scenarioId,
        String scenarioCode,
        String scenarioBlock,
        ScenarioOutcome outcome,
        int loyaltyScore,
        int safetyScore,
        int choicesMade,
        boolean hadTimeout,
        boolean allRoleStepsFollowed,
        Instant startedAt,
        Instant completedAt) {
}
