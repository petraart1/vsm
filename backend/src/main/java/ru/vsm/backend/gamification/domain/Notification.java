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

/**
 * Уведомление игрока (новая ачивка / личный рекорд / рост в лидерборде / рекомендация).
 * Создаётся в {@code GamificationAccrualService} в том же обработчике
 * {@code ScenarioCompletedEvent}, что и начисление очков — идемпотентность обеспечивает
 * проверка по {@code userProgressId} в {@code gamification_accrual_log} перед обработкой
 * события (повтор события не доходит до создания уведомлений).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gamification_notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    /** Прохождение, вызвавшее уведомление; null, если не привязано к конкретному прохождению. */
    @Column(name = "source_user_progress_id")
    private UUID sourceUserProgressId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;
}
