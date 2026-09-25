package ru.vsm.backend.scenario.event;

import java.util.UUID;
import ru.vsm.backend.scenario.web.dto.ChoiceAppliedResponse;

/**
 * Доменное событие: состояние прохождения изменилось в результате применённого выбора —
 * публикуется на каждый вызов {@code ScenarioPlayService.choose}/{@code timeout}, независимо от
 * того, кто инициировал изменение (REST-запрос игрока или серверный автовызов по истечении
 * таймера — см. {@code ru.vsm.backend.ws.ProgressChannelRegistry}).
 *
 * <p>Единственный слушатель на момент введения события — WebSocket-пакет {@code ru.vsm.backend.ws}
 * (точка публикации "живых" обновлений шкал/узла для подключённых клиентов, задача про
 * WebSocket-таймер). {@code scenario} — единственный источник истины и публикует событие
 * синхронно внутри той же {@code @Transactional} операции, что сохраняет {@code UserProgress};
 * слушатели читают его через {@code @TransactionalEventListener(phase = AFTER_COMMIT,
 * fallbackExecution = true)} (тот же паттерн, что и у {@link ScenarioCompletedEvent}), чтобы не
 * рассылать состояние, которое в итоге не закоммитится.
 *
 * @param playerId      владелец прохождения ({@code UserProgress.userId}) — для проверки
 *                       адресности на стороне WebSocket-слушателя
 * @param appliedChoice  уже применённый выбор и новое состояние прохождения (то же тело, что
 *                       REST-клиент получает в ответ на {@code POST .../choices/{id}} или
 *                       {@code POST .../timeout})
 */
public record ProgressStateChangedEvent(UUID playerId, ChoiceAppliedResponse appliedChoice) {
}
