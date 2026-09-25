package ru.vsm.backend.gamification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.domain.CompetencyScore;

public interface CompetencyScoreRepository extends JpaRepository<CompetencyScore, UUID> {

    List<CompetencyScore> findByPlayerId(UUID playerId);

    Optional<CompetencyScore> findByPlayerIdAndBlock(UUID playerId, String block);

    long countByPlayerId(UUID playerId);
}
