package ru.vsm.backend.gamification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Профиль игрока (проводника). {@link #id} — тот же {@code userId}, что публикует
 * {@code scenario.event.ScenarioCompletedEvent}; отдельного домена аутентификации в MVP нет,
 * поэтому id не сгенерирован здесь, а приходит вместе с первым событием/запросом.
 *
 * <p>Строка создаётся лениво в {@code GamificationAccrualService} при первом начисленном
 * событии для этого игрока.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gamification_player_profile")
public class PlayerProfile {

    @Id
    private UUID id;

    /** Может быть null — домена аутентификации нет, имя не всегда известно. */
    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "total_score", nullable = false)
    @Builder.Default
    private int totalScore = 0;

    @Column(name = "scenarios_completed", nullable = false)
    @Builder.Default
    private int scenariosCompleted = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Момент последнего РЕАЛЬНОГО действия игрока (начисление за прохождение сценария, см.
     * {@code GamificationAccrualService#processEvent}) — в отличие от {@link #updatedAt}, не
     * меняется, когда профиль трогает только {@code PointsExpiryService} (сгорание баллов не
     * должно "сбрасывать" неактивность самим фактом своей работы).
     */
    @Column(name = "last_activity_at", nullable = false)
    @Builder.Default
    private Instant lastActivityAt = Instant.now();

    /**
     * Момент последнего сгорания баллов этого игрока за неактивность ({@code
     * PointsExpiryService}); {@code null}, если ещё ни разу не сгорали. Вместе с {@link
     * #lastActivityAt} задаёт точку отсчёта следующего периода неактивности — см. javadoc
     * {@code PointsExpiryService#referencePoint}.
     */
    @Column(name = "last_points_expiry_at")
    private Instant lastPointsExpiryAt;
}
