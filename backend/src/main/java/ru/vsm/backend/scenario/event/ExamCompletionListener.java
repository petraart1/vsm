package ru.vsm.backend.scenario.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.vsm.backend.scenario.domain.UserProgress;
import ru.vsm.backend.scenario.repository.UserProgressRepository;
import ru.vsm.backend.scenario.service.ExamService;

/** Слушатель {@link ScenarioCompletedEvent} на стороне режима экзамена: отмечает пункт экзамена */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamCompletionListener {

    private final UserProgressRepository userProgressRepository;
    private final ExamService examService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onScenarioCompleted(ScenarioCompletedEvent event) {
        try {
            UserProgress progress = userProgressRepository.findById(event.userProgressId()).orElse(null);
            if (progress == null || progress.getExamId() == null) {
                return;
            }
            examService.recordScenarioCompleted(progress.getExamId(), event.scenarioId(), event.outcome(),
                    event.loyaltyScore(), event.safetyScore());
        } catch (Exception ex) {
            // Сбой учёта пункта экзамена не должен ронять остальную обработку события (например,
            // начисление очков в gamification) — см. тот же приём в ScenarioCompletedEventListener.
            log.error("Учёт завершения пункта экзамена для userProgressId={} упал", event.userProgressId(), ex);
        }
    }
}
