package ru.vsm.backend.gamification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.vsm.backend.scenario.domain.ExamGrade;

/**
 * Журнал одного бонусного начисления за завершение экзамена целиком ({@code ExamCompletedEvent}).
 *
 * <p>Отдельно от {@link AccrualLogEntry}, который пишется по каждому завершённому сценарию (в т.ч.
 * пунктам экзамена — но с {@code totalPointsAwarded = 0}, см. Javadoc {@code
 * GamificationAccrualService#processEvent}, поле {@code awardable}): экзамен вознаграждается
 * итоговым бонусом по оценке ровно один раз, а не за каждый входящий в него сценарий.
 *
 * <p>{@link #examId} уникален (см. {@code uq_exam_accrual_log_exam}) — механизм идемпотентности:
 * {@code ExamAccrualService} проверяет, нет ли уже записи с таким {@code examId}, прежде чем
 * начислить бонус повторно доставленному событию.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gamification_exam_accrual_log")
public class ExamAccrualLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Ключ идемпотентности; без FK на scenario.exam, связь между доменами по id. */
    @Column(name = "exam_id", nullable = false, unique = true)
    private UUID examId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExamGrade grade;

    @Column(name = "bonus_points_awarded", nullable = false)
    private int bonusPointsAwarded;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
