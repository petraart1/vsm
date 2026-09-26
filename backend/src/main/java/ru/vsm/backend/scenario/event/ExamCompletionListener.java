package ru.vsm.backend.scenario.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.vsm.backend.scenario.domain.UserProgress;
import ru.vsm.backend.scenario.repository.UserProgressRepository;
import ru.vsm.backend.scenario.service.ExamService;

/**
 * Слушатель {@link ScenarioCompletedEvent} на стороне режима экзамена: отмечает пункт экзамена
 * завершённым (см. {@link ExamService#recordScenarioCompleted}), если завершённое прохождение
 * принадлежит какому-либо экзамену.
 *
 * <p><b>Почему отдельный бин, а не метод {@link ExamService} самого на себя</b>: раньше
 * {@code onScenarioCompleted} и {@code recordScenarioCompleted} были двумя методами одного и того
 * же {@code ExamService}, и первый вызывал второй как {@code this.recordScenarioCompleted(...)}
 * (self-invocation). Spring оборачивает {@code @Transactional} в прокси, который перехватывает
 * только вызовы бина ИЗВНЕ — self-invocation идёт напрямую на реальный объект в обход прокси, и
 * объявленная пропагация ({@code REQUIRES_NEW}) попросту не применялась: метод присоединялся к
 * уже закоммиченной (после {@code AFTER_COMMIT}) транзакции, `SELECT`ы отрабатывали без ошибок, но
 * `save()` физически не коммитились — экзамен нельзя было пройти целиком без единой строки в
 * логе. Ровно тот же баг-паттерн уже был найден и исправлен для
 * {@code GamificationAccrualService#processEvent} тем же приёмом: обработчик события — отдельный
 * бин ({@link ru.vsm.backend.gamification.event.ScenarioCompletedEventListener}), вызывающий метод
 * сервиса через его Spring-прокси, а не самого себя.
 *
 * <p>{@code AFTER_COMMIT}/{@code fallbackExecution = true} — по тем же причинам, что и у
 * gamification-слушателя (см. его Javadoc): видеть только зафиксированные прохождения, не ронять
 * завершение сценария сбоем здесь, работать и без активной транзакции вокруг {@code publishEvent}
 * (тесты).
 */
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
