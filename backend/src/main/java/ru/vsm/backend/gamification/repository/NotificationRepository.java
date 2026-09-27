package ru.vsm.backend.gamification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.domain.Notification;
import ru.vsm.backend.gamification.domain.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByPlayerIdOrderByCreatedAtDesc(UUID playerId);

    List<Notification> findByPlayerIdAndReadAtIsNullOrderByCreatedAtDesc(UUID playerId);

    List<Notification> findByPlayerIdAndReadAtIsNull(UUID playerId);

    /** Использует {@code PointsExpiryService} для дедупликации {@code POINTS_EXPIRING}: если такое */
    boolean existsByPlayerIdAndTypeAndCreatedAtGreaterThanEqual(
            UUID playerId, NotificationType type, Instant createdAtFrom);
}
