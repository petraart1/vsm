package ru.vsm.backend.gamification.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.domain.AchievementCode;
import ru.vsm.backend.gamification.domain.PlayerAchievement;

public interface PlayerAchievementRepository extends JpaRepository<PlayerAchievement, UUID> {

    List<PlayerAchievement> findByPlayerId(UUID playerId);

    boolean existsByPlayerIdAndAchievementCode(UUID playerId, AchievementCode achievementCode);
}
