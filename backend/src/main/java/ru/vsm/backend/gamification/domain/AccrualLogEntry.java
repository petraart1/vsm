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
import ru.vsm.backend.scenario.domain.ScenarioOutcome;

/**
 * Журнал одного начисления очков за одно завершённое прохождение сценария.
 *
 * <p>{@link #userProgressId} уникален (см. {@code uq_accrual_log_user_progress}) — это и есть
 * механизм идемпотентности: перед обработкой {@code ScenarioCompletedEvent}
 * {@code GamificationAccrualService} проверяет, нет ли уже записи с таким
 * {@code userProgressId}, и если есть — пропускает событие без повторного начисления.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gamification_accrual_log")
public class AccrualLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Ключ идемпотентности; без FK на scenario.user_progress — связь между доменами по id. */
    @Column(name = "user_progress_id", nullable = false, unique = true)
    private UUID userProgressId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @Column(name = "scenario_code", nullable = false, length = 100)
    private String scenarioCode;

    @Column(nullable = false, length = 32)
    private String block;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScenarioOutcome outcome;

    @Column(name = "loyalty_points_awarded", nullable = false)
    private int loyaltyPointsAwarded;

    @Column(name = "safety_points_awarded", nullable = false)
    private int safetyPointsAwarded;

    @Column(name = "total_points_awarded", nullable = false)
    private int totalPointsAwarded;

    @Column(name = "had_timeout", nullable = false)
    @Builder.Default
    private boolean hadTimeout = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
