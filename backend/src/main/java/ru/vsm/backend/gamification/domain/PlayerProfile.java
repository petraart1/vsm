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

/** Профиль игрока (проводника). {@link #id} — тот же {@code userId}, что публикует */
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

    /** Момент последнего РЕАЛЬНОГО действия игрока (начисление за прохождение сценария, см. */
    @Column(name = "last_activity_at", nullable = false)
    @Builder.Default
    private Instant lastActivityAt = Instant.now();

    /** Момент последнего сгорания баллов этого игрока за неактивность ({@code */
    @Column(name = "last_points_expiry_at")
    private Instant lastPointsExpiryAt;
}
