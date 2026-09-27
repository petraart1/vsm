package ru.vsm.backend.gamification.award.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.award.domain.CustomAwardGrant;

public interface CustomAwardGrantRepository extends JpaRepository<CustomAwardGrant, UUID> {

    List<CustomAwardGrant> findByPlayerId(UUID playerId);

    boolean existsByAwardIdAndPlayerId(UUID awardId, UUID playerId);

    long countByAwardId(UUID awardId);
}
