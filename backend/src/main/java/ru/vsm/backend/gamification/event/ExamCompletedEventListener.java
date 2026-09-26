package ru.vsm.backend.gamification.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.vsm.backend.gamification.service.ExamAccrualService;
import ru.vsm.backend.scenario.event.ExamCompletedEvent;

/**
 * Слушатель {@link ExamCompletedEvent} на стороне gamification: бонус очков по итоговой оценке
 * экзамена + ачивка "Сертификат" за оценку "отлично" (см. {@link ExamAccrualService}).
 *
 * <p>{@code @TransactionalEventListener(AFTER_COMMIT)} и вызов через отдельный бин (не
 * self-invocation) — та же схема, что и у {@link ScenarioCompletedEventListener}/
 * {@code ru.vsm.backend.scenario.event.ExamCompletionListener}: видеть только зафиксированный
 * {@code Exam.status = COMPLETED}, не ронять остальную обработку события сбоем здесь, и главное —
 * не наступать на self-invocation баг-паттерн (см. подробный разбор в {@code ExamService}
 * Javadoc), из-за которого метод с {@code REQUIRES_NEW} не коммитил бы свои изменения, если бы
 * вызывался как метод того же объекта, а не через Spring-прокси.
 */
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
