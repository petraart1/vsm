package ru.vsm.backend.gamification.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.vsm.backend.gamification.domain.PlayerProfile;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, UUID> {

    List<PlayerProfile> findByOrderByTotalScoreDesc(Pageable pageable);

    @Query("""
            select count(p) + 1 from PlayerProfile p
            where p.totalScore > (select p2.totalScore from PlayerProfile p2 where p2.id = :playerId)
            """)
    long findRankByPlayerId(UUID playerId);
}
