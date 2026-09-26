package ru.vsm.backend.gamification.team.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.team.domain.Team;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByCode(String code);
}
