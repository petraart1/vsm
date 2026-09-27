package ru.vsm.backend.scenario.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.UserProgress;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {

    List<UserProgress> findByUserId(UUID userId);

    Optional<UserProgress> findByUserIdAndScenarioIdAndStatus(UUID userId, UUID scenarioId, ProgressStatus status);

    /** Как {@link #findById(Object)}, но с {@code SELECT ... FOR UPDATE} (пессимистичная блокировка */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProgress p where p.id = :id")
    Optional<UserProgress> findByIdForUpdate(@Param("id") UUID id);

    /** Есть ли хоть одно прохождение (в любом статусе) этого сценария. Используется */
    boolean existsByScenarioId(UUID scenarioId);

    /** Есть ли у игрока УЖЕ ДРУГОЕ (не {@code excludedProgressId}) завершённое прохождение этого */
    boolean existsByUserIdAndScenarioIdAndStatusAndIdNot(
            UUID userId, UUID scenarioId, ProgressStatus status, UUID excludedProgressId);
}
