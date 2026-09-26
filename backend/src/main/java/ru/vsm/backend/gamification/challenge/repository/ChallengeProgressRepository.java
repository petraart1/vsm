package ru.vsm.backend.gamification.challenge.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.challenge.domain.ChallengeProgress;

public interface ChallengeProgressRepository extends JpaRepository<ChallengeProgress, UUID> {

    Optional<ChallengeProgress> findByChallengeIdAndPlayerId(UUID challengeId, UUID playerId);

    List<ChallengeProgress> findByPlayerId(UUID playerId);
}
