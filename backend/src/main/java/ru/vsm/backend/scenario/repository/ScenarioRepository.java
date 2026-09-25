package ru.vsm.backend.scenario.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.scenario.domain.Scenario;

public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {

    Optional<Scenario> findByCode(String code);

    boolean existsByCode(String code);
}
