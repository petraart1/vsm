package ru.vsm.backend.gamification.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.vsm.backend.gamification.domain.AccrualLogEntry;

public interface AccrualLogRepository extends JpaRepository<AccrualLogEntry, UUID> {

    boolean existsByUserProgressId(UUID userProgressId);

    /** Лучший результат одного прохождения этого сценария игроком (до текущего события). */
    @Query("""
            select max(a.totalPointsAwarded) from AccrualLogEntry a
            where a.playerId = :playerId and a.scenarioCode = :scenarioCode
            """)
    Optional<Integer> findMaxTotalPointsByPlayerIdAndScenarioCode(UUID playerId, String scenarioCode);
}
