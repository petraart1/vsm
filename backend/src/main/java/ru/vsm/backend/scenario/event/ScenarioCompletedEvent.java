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
 * @param examMode        {@code true}, если это прохождение — пункт экзамена ({@code UserProgress.examMode},
 *        см. {@code ExamService}), а не обычная тренировочная попытка. Сигнал для gamification:
 *        полное начисление очков/ачивок/челленджей не выдаётся за экзаменационные прохождения
 *        (сам факт участия и итоговая аттестация вознаграждаются отдельно, при завершении всего
 *        экзамена — {@link ru.vsm.backend.scenario.domain.ExamGrade}/{@code ExamCompletedEvent}),
 *        иначе один и тот же набор сценариев экзамена приносил бы очки дважды: и за каждый пункт
 *        по отдельности, и за итоговую оценку.
 * @param firstCompletion {@code true}, если это первое когда-либо завершённое ({@code COMPLETED})
 *        прохождение ИМЕННО этого сценария этим игроком (независимо от {@link #examMode}) —
 *        {@code false} для повторного прохождения уже когда-то завершённого сценария. Сигнал для
 *        gamification: полные очки/ачивки/челленджи только за первое прохождение — иначе один и
 *        тот же лёгкий сценарий можно фармить бесконечно, каждый раз получая новый
 *        {@code userProgressId} (см. находку аудита безопасности про replay уже {@code COMPLETED}
 *        сценариев). Компетенции по шкалам (см. {@code CompetencyScore}) и разбор решений
 *        (feedback) НЕ зависят от этого флага — аналитика должна видеть все реальные попытки
 *        игрока, а не только первую.
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
        Instant completedAt,
        boolean examMode,
        boolean firstCompletion) {
}
