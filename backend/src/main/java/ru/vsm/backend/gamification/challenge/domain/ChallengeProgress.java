package ru.vsm.backend.gamification.challenge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Прогресс одного игрока по одному {@link Challenge}. Одна строка на пару (challenge, player) —
 * см. {@code uq_challenge_progress_challenge_player}.
 *
 * <p>Обновляется в {@code GamificationAccrualService} в том же обработчике
 * {@code ScenarioCompletedEvent}, что и начисление очков — отдельной идемпотентности здесь не
 * требуется: весь метод пропускается целиком при повторной доставке уже обработанного
 * {@code userProgressId} (см. {@code gamification_accrual_log}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gamification_challenge_progress",
        uniqueConstraints = @UniqueConstraint(name = "uq_challenge_progress_challenge_player",
                columnNames = {"challenge_id", "player_id"}))
public class ChallengeProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "current_value", nullable = false)
    @Builder.Default
    private int currentValue = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean completed = false;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
