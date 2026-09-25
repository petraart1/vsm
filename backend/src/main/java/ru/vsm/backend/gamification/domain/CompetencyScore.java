package ru.vsm.backend.gamification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Накопленные очки компетенций одного игрока по одному блоку ситуаций
 * (boarding/baggage/safety/seating/comfort/catering/medical/lost_found/conflict/misc),
 * раздельно по двум шкалам. Одна строка на пару (player, block) — см.
 * {@code uq_competency_score_player_block}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gamification_competency_score")
public class CompetencyScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(nullable = false, length = 32)
    private String block;

    @Column(name = "loyalty_points", nullable = false)
    @Builder.Default
    private int loyaltyPoints = 0;

    @Column(name = "safety_points", nullable = false)
    @Builder.Default
    private int safetyPoints = 0;

    /** Число прохождений этого блока игроком (для мини-прогресса на экране профиля). */
    @Column(name = "scenarios_completed", nullable = false)
    @Builder.Default
    private int scenariosCompleted = 0;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
