package ru.vsm.backend.gamification.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.domain.AccrualLogEntry;

public interface AccrualLogRepository extends JpaRepository<AccrualLogEntry, UUID> {

    boolean existsByUserProgressId(UUID userProgressId);
}
