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

/** Факт получения ачивки игроком. Условие/описание — см. {@link AchievementCode}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gamification_player_achievement")
public class PlayerAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_code", nullable = false, length = 64)
    private AchievementCode achievementCode;

    @Column(name = "earned_at", nullable = false)
    @Builder.Default
    private Instant earnedAt = Instant.now();
}
