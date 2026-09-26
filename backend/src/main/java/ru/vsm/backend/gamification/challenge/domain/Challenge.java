package ru.vsm.backend.gamification.challenge.domain;

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

/**
 * Челлендж месяца: игровая цель с периодом действия и наградой. Каталог заполняется идемпотентно
 * при старте приложения (см. {@code challenge.service.ChallengeSeeder}) по коду вида
 * {@code "<ключ шаблона>-<год>-<месяц>"} — новый календарный месяц порождает новые строки без
 * релиза, прошлые остаются в истории (и просто больше не входят в выборку активных по
 * {@link #startsAt}/{@link #endsAt}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gamification_challenge")
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Уникальный код, включает год-месяц — единственный ключ идемпотентности сидера. */
    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", nullable = false, length = 32)
    private ChallengeGoalType goalType;

    /**
     * Фильтр по блоку ситуаций. Обязателен для {@code BLOCK_SCENARIOS_NO_FAILURE}; для остальных
     * типов {@code null} означает "любой блок".
     */
    @Column(name = "target_block", length = 32)
    private String targetBlock;

    /** Целевое значение счётчика прогресса ({@code N} в описании типа цели). */
    @Column(name = "target_count", nullable = false)
    private int targetCount;

    /** Порог шкалы безопасности; используется только типом {@code SAFETY_STREAK}. */
    @Column(name = "safety_threshold")
    private Integer safetyThreshold;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "reward_points", nullable = false)
    private int rewardPoints;

    /**
     * Имя константы {@code ru.vsm.backend.gamification.domain.AchievementCode}; {@code null} —
     * награда только очками, без ачивки.
     */
    @Column(name = "reward_achievement_code", length = 64)
    private String rewardAchievementCode;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
