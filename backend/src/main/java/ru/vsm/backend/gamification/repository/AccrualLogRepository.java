package ru.vsm.backend.gamification.repository;

import java.time.Instant;
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

    /**
     * Сумма уже начисленных игроку очков начиная с {@code since} (включительно) — основа для
     * суточного антифрод-лимита в {@code GamificationAccrualService}. {@code coalesce} — 0, а не
     * {@code null}, если сегодня начислений ещё не было.
     */
    @Query("""
            select coalesce(sum(a.totalPointsAwarded), 0) from AccrualLogEntry a
            where a.playerId = :playerId and a.createdAt >= :since
            """)
    int sumTotalPointsByPlayerIdSince(UUID playerId, Instant since);
}
