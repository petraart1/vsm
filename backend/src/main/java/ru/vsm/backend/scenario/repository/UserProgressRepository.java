package ru.vsm.backend.scenario.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.UserProgress;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {

    List<UserProgress> findByUserId(UUID userId);

    Optional<UserProgress> findByUserIdAndScenarioIdAndStatus(UUID userId, UUID scenarioId, ProgressStatus status);
}
