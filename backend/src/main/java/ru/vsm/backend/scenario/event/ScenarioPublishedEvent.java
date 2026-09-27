package ru.vsm.backend.scenario.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Доменное событие: в каталоге появился новый сценарий (создан через редактор сценариев,
 * {@code EditorScenarioController}, включая импорт из markdown) — в отличие от
 * {@link ScenarioCompletedEvent}, не связано с чьим-то прохождением.
 *
 * <p>Публикуется через {@link org.springframework.context.ApplicationEventPublisher#publishEvent(Object)}
 * ровно один раз, когда {@code EditorScenarioController} сохраняет сценарий с ещё не существующим
 * {@code code} (обновление уже существующего сценария этого события не порождает). Сид сценариев
 * при старте приложения ({@code ScenarioSeedLoader}) это событие не публикует — оно только про
 * реальную публикацию контента поверх уже работающего приложения.
 *
 * <p>Слушает: <b>gamification</b> — рассылает уведомление {@code NEW_SCENARIO} всем уже известным
 * профилям игроков.
 *
 * @param scenarioId  id нового сценария
 * @param scenarioCode человекочитаемый код сценария
 * @param title        заголовок сценария (для текста уведомления)
 * @param block        блок датасета (boarding/medical/...)
 * @param publishedAt  момент публикации
 */
public record ScenarioPublishedEvent(
        UUID scenarioId,
        String scenarioCode,
        String title,
        String block,
        Instant publishedAt) {
}
