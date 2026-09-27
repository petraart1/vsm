package ru.vsm.backend.gamification.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.vsm.backend.gamification.service.ExamAccrualService;
import ru.vsm.backend.scenario.event.ExamCompletedEvent;

/** Слушатель {@link ExamCompletedEvent} на стороне gamification: бонус очков по итоговой оценке */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamCompletedEventListener {

    private final ExamAccrualService examAccrualService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExamCompleted(ExamCompletedEvent event) {
        try {
            examAccrualService.processExam(event);
        } catch (Exception ex) {
            log.error("Начисление бонуса за экзамен examId={} упало", event.examId(), ex);
        }
    }
}
