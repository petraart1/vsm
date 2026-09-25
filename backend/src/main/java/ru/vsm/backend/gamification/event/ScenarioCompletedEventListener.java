package ru.vsm.backend.gamification.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.vsm.backend.gamification.service.GamificationAccrualService;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;

/**
 * Слушатель {@link ScenarioCompletedEvent} на стороне gamification.
 *
 * <p><b>Почему {@code @TransactionalEventListener(AFTER_COMMIT)}, а не простой
 * {@code @EventListener}</b>: событие публикуется внутри транзакции при завершении сценария,
 * которая переводит {@code UserProgress.status} в {@code COMPLETED} (и пишет историю выборов).
 * Обычный {@code @EventListener} выполняется синхронно в той же транзакции — если начисление
 * очков здесь упадёт, откатится и завершение прохождения, хотя эти два домена по контракту
 * связаны только событием, не общей транзакцией. AFTER_COMMIT гарантирует: (1) gamification
 * видит только зафиксированные прохождения, (2) сбой в начислении не может откатить сценарий,
 * (3) обработка идёт в собственной новой транзакции (см. {@code GamificationAccrualService#processEvent}, {@code @Transactional}).
 *
 * <p>{@code fallbackExecution = true} — на случай, если событие будет опубликовано вне
 * активной транзакции (например, в тесте через
 * {@code ApplicationEventPublisher#publishEvent} без транзакции): без этого флага
 * Spring молча не вызвал бы listener вовсе, что для начисления очков хуже, чем обработка
 * без строгой гарантии "после commit".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScenarioCompletedEventListener {

    private final GamificationAccrualService accrualService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onScenarioCompleted(ScenarioCompletedEvent event) {
        try {
            accrualService.processEvent(event);
        } catch (Exception ex) {
            // Начисление не должно "уронить" остальную обработку события (например, feedback).
            log.error("Начисление очков за userProgressId={} упало", event.userProgressId(), ex);
        }
    }
}
