package ru.vsm.backend.ws;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.event.ProgressStateChangedEvent;
import ru.vsm.backend.scenario.service.ScenarioPlayService;
import ru.vsm.backend.scenario.service.exception.ChoiceNotAvailableException;
import ru.vsm.backend.scenario.service.exception.NoActiveTimerException;
import ru.vsm.backend.scenario.service.exception.ProgressAlreadyCompletedException;
import ru.vsm.backend.scenario.service.exception.ProgressNotFoundException;
import ru.vsm.backend.scenario.web.dto.ChoiceAppliedResponse;
import ru.vsm.backend.ws.dto.ProgressWsMessage;
import tools.jackson.databind.ObjectMapper;

/**
 * Живой WebSocket-канал прохождения: реестр подключённых сессий по {@code progressId}, тикер
 * обратного отсчёта (Reactor {@link Flux#interval}) и рассылка событий {@code tick}/
 * {@code timeout}/{@code state}/{@code completed} (см. {@link ProgressWsMessage}).
 *
 * <p><b>REST остаётся источником истины</b>: тикер существует только пока к прохождению
 * подключена хотя бы одна WebSocket-сессия (канал создаётся при первом подключении и удаляется
 * при последнем отключении, см. {@link #register}/{@link #unregister}) — клиент без WebSocket не
 * ломается, т.к. {@code ScenarioPlayService.choose} самостоятельно подставляет
 * {@code defaultChoice} при просроченном дедлайне (см. Javadoc сервиса).
 *
 * <p><b>Серверный автотаймаут</b> ({@link #applyServerTimeout}) вызывает тот же
 * {@code ScenarioPlayService.timeout}, что и REST-эндпоинт {@code POST .../timeout} — та же
 * пессимистичная блокировка прохождения, что и у любого REST-запроса (см.
 * {@code UserProgressRepository.findByIdForUpdate}). Если состояние уже успело измениться
 * конкурентно (игрок сам выбрал вариант через REST секундой раньше — прохождение уже не
 * {@code IN_PROGRESS}, либо уже на другом узле без дефолтного выбора), тикер просто игнорирует
 * исключение, не шлёт ничего лишнего: REST-выбор уже разослал {@code state} через
 * {@link #onProgressStateChanged}.
 *
 * <p><b>Точка подписки на изменения из REST</b>: {@link #onProgressStateChanged} слушает
 * {@link ProgressStateChangedEvent}, который {@code ScenarioPlayService} публикует на каждое
 * применение выбора — независимо от того, кто его инициировал (REST-запрос игрока или тикер этого
 * класса). Поэтому REST-выбор игрока с открытой WebSocket-сессией того же прохождения тоже
 * рассылает {@code state} всем подключённым сессиям, и здесь же тикер перепланируется под новый
 * узел (или останавливается, если прохождение завершилось или узел без таймера).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgressChannelRegistry {

    private final ScenarioPlayService scenarioPlayService;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<UUID, ProgressChannel> channels = new ConcurrentHashMap<>();

    /** Подписка на состояние прохождения: набор сессий и текущий тикер обратного отсчёта (если есть). */
    private record ProgressChannel(UUID playerId, Set<WebSocketSession> sessions, AtomicReference<Disposable> ticker) {

        private static ProgressChannel empty(UUID playerId) {
            return new ProgressChannel(playerId, ConcurrentHashMap.newKeySet(), new AtomicReference<>());
        }
    }

    void register(UUID progressId, UUID playerId, WebSocketSession session) {
        channels.computeIfAbsent(progressId, id -> ProgressChannel.empty(playerId)).sessions().add(session);
    }

    void unregister(UUID progressId, WebSocketSession session) {
        ProgressChannel channel = channels.get(progressId);
        if (channel == null) {
            return;
        }
        channel.sessions().remove(session);
        if (channel.sessions().isEmpty()) {
            disposeTicker(channel);
            channels.remove(progressId, channel);
        }
    }

    /** Отправляет сообщение только этой (обычно только что подключившейся) сессии. */
    void sendTo(WebSocketSession session, ProgressWsMessage message) {
        sendQuietly(session, message);
    }

    /**
     * (Пере)запускает тикер обратного отсчёта до {@code deadline}: раз в секунду шлёт {@code tick}
     * всем сессиям канала, а по достижении дедлайна — вызывает {@link #applyServerTimeout}.
     * Предыдущий тикер этого канала (если был) останавливается. Не блокирующий: считает и шлёт на
     * {@link Schedulers#boundedElastic()}, не на реакторном parallel-планировщике.
     */
    void scheduleTicker(UUID progressId, UUID playerId, Instant deadline) {
        ProgressChannel channel = channels.get(progressId);
        if (channel == null) {
            return;
        }
        disposeTicker(channel);
        Disposable subscription = Flux.interval(Duration.ZERO, Duration.ofSeconds(1))
                .publishOn(Schedulers.boundedElastic())
                .map(tick -> Duration.between(Instant.now(), deadline).getSeconds())
                .takeWhile(remaining -> remaining >= 0)
                .doOnNext(remaining -> broadcast(progressId, ProgressWsMessage.tick(progressId, remaining)))
                .doOnComplete(() -> applyServerTimeout(progressId, playerId))
                .subscribe(
                        remaining -> { },
                        error -> log.warn("Ошибка тикера прохождения {}: {}", progressId, error.getMessage(), error));
        channel.ticker().set(subscription);
    }

    private void disposeTicker(ProgressChannel channel) {
        Disposable previous = channel.ticker().getAndSet(null);
        if (previous != null && !previous.isDisposed()) {
            previous.dispose();
        }
    }

    /**
     * Вызывается тикером по достижении дедлайна узла — тот же метод сервиса, что и явный REST
     * {@code POST .../timeout}, поэтому дублирующегося/конфликтующего с REST кода блокировки нет.
     * Гонка с параллельным REST-выбором того же прохождения разрешается на уровне
     * {@code UserProgressRepository.findByIdForUpdate}: кто раньше — тот и применяется, второй
     * получает доменное исключение здесь и молча игнорируется (см. Javadoc класса).
     */
    private void applyServerTimeout(UUID progressId, UUID playerId) {
        try {
            ChoiceAppliedResponse response = scenarioPlayService.timeout(progressId, playerId);
            broadcast(progressId, ProgressWsMessage.fromAppliedChoice(
                    ProgressWsMessage.TYPE_TIMEOUT, progressId,
                    response.appliedChoiceId(), response.appliedChoiceCode(), response.wasTimeout(),
                    response.loyaltyDelta(), response.safetyDelta(), response.loyaltyScore(), response.safetyScore(),
                    response.status(), response.finalOutcome(), response.nextNode()));
        } catch (ProgressAlreadyCompletedException | NoActiveTimerException | ChoiceNotAvailableException e) {
            log.debug("Серверный автотаймаут прохождения {} пропущен — состояние уже изменилось конкурентно: {}",
                    progressId, e.getMessage());
        } catch (ProgressNotFoundException e) {
            log.warn("Серверный автотаймаут: прохождение {} не найдено", progressId, e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProgressStateChanged(ProgressStateChangedEvent event) {
        ChoiceAppliedResponse r = event.appliedChoice();
        ProgressChannel channel = channels.get(r.progressId());
        if (channel == null) {
            return;
        }

        broadcast(r.progressId(), ProgressWsMessage.fromAppliedChoice(
                ProgressWsMessage.TYPE_STATE, r.progressId(),
                r.appliedChoiceId(), r.appliedChoiceCode(), r.wasTimeout(),
                r.loyaltyDelta(), r.safetyDelta(), r.loyaltyScore(), r.safetyScore(),
                r.status(), r.finalOutcome(), r.nextNode()));

        if (r.status() == ProgressStatus.COMPLETED) {
            broadcast(r.progressId(), ProgressWsMessage.fromAppliedChoice(
                    ProgressWsMessage.TYPE_COMPLETED, r.progressId(),
                    r.appliedChoiceId(), r.appliedChoiceCode(), r.wasTimeout(),
                    r.loyaltyDelta(), r.safetyDelta(), r.loyaltyScore(), r.safetyScore(),
                    r.status(), r.finalOutcome(), r.nextNode()));
            // Тикер больше не нужен, но канал/сессии намеренно НЕ закрываются здесь: если это
            // изменение вызвал сам тикер (см. applyServerTimeout), он ещё не отправил свой
            // специфичный "timeout" этим же сессиям — преждевременное закрытие/удаление канала
            // потеряло бы это сообщение. Сессии закрывает клиент либо обычное disconnect
            // (см. afterConnectionClosed/unregister).
            disposeTicker(channel);
        } else if (r.nextNode() != null && r.nextNode().deadlineAt() != null) {
            scheduleTicker(r.progressId(), event.playerId(), r.nextNode().deadlineAt());
        } else {
            disposeTicker(channel);
        }
    }

    private void broadcast(UUID progressId, ProgressWsMessage message) {
        ProgressChannel channel = channels.get(progressId);
        if (channel == null) {
            return;
        }
        for (WebSocketSession session : channel.sessions()) {
            sendQuietly(session, message);
        }
    }

    private void sendQuietly(WebSocketSession session, ProgressWsMessage message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (IOException e) {
            log.warn("Не удалось отправить WS-сообщение сессии {}: {}", session.getId(), e.getMessage());
        }
    }

}
