package ru.vsm.backend.gamification.team.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.team.domain.TeamMembership;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, UUID> {

    Optional<TeamMembership> findByPlayerId(UUID playerId);

    List<TeamMembership> findByTeamId(UUID teamId);

    long countByTeamId(UUID teamId);
}
