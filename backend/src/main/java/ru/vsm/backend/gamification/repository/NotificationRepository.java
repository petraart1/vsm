package ru.vsm.backend.gamification.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByPlayerIdOrderByCreatedAtDesc(UUID playerId);

    List<Notification> findByPlayerIdAndReadAtIsNullOrderByCreatedAtDesc(UUID playerId);

    List<Notification> findByPlayerIdAndReadAtIsNull(UUID playerId);
}
