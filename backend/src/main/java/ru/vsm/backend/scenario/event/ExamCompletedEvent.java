package ru.vsm.backend.scenario.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.vsm.backend.scenario.domain.ExamGrade;

/**
 * Доменное событие: игрок завершил экзамен целиком (все сценарии {@link ru.vsm.backend.scenario.domain.Exam}
 * пройдены). Публикуется через {@link org.springframework.context.ApplicationEventPublisher#publishEvent(Object)}
 * при переходе {@code Exam.status} в {@link ru.vsm.backend.scenario.domain.ExamStatus#COMPLETED}
 * (см. {@code ExamService.finishExam}), в отличие от {@link ScenarioCompletedEvent}, который
 * продолжает публиковаться на каждый отдельный сценарий экзамена (в т.ч. внутри экзамена) — экзамен
 * не подавляет обычное начисление очков/аналитику по каждому сценарию, это отдельный агрегирующий сигнал.
 *
 * <p><b>gamification</b> слушает это событие ({@code gamification.event.ExamCompletedEventListener}
 * -&gt; {@code gamification.service.ExamAccrualService}): бонус очков по таблице оценок и ачивка
 * "Сертификат" за {@link ExamGrade#EXCELLENT} — отдельно от очков за отдельные сценарии экзамена
 * (которые намеренно не начисляются полностью, см. Javadoc {@code
 * GamificationAccrualService#processEvent}, поле {@code awardable}), иначе экзамен вознаграждался
 * бы дважды. feedback это событие на момент введения не слушает — контракт зафиксирован заранее
 * для будущей интеграции (например, попадание результата в отдельный раздел аналитики).
 *
 * @param examId         id {@link ru.vsm.backend.scenario.domain.Exam}
 * @param playerId       id игрока
 * @param avgLoyaltyScore средняя шкала "лояльность пассажира" по сценариям экзамена (0..100)
 * @param avgSafetyScore  средняя шкала "рейтинг безопасности" по сценариям экзамена (0..100)
 * @param successRate     доля сценариев экзамена с исходом {@code SUCCESS} (0..1)
 * @param grade           итоговая оценка (см. {@link ExamGrade} — пороги в javadoc enum'а)
 * @param weakBlocks      блоки датасета, где сценарий не завершился {@code SUCCESS}, от худшего
 *                        к менее слабому (см. {@code ExamService.computeWeakBlocks})
 * @param startedAt       момент создания экзамена
 * @param finishedAt      момент завершения последнего сценария экзамена
 */
public record ExamCompletedEvent(
        UUID examId,
        UUID playerId,
        double avgLoyaltyScore,
        double avgSafetyScore,
        double successRate,
        ExamGrade grade,
        List<String> weakBlocks,
        Instant startedAt,
        Instant finishedAt) {
}
