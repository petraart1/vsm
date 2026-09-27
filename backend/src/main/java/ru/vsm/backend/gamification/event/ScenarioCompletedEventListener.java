package ru.vsm.backend.gamification.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.vsm.backend.gamification.service.GamificationAccrualService;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;

/** Слушатель {@link ScenarioCompletedEvent} на стороне gamification. */
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
