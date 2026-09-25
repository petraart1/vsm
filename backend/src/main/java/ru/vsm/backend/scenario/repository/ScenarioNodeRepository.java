package ru.vsm.backend.scenario.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.scenario.domain.ScenarioNode;

public interface ScenarioNodeRepository extends JpaRepository<ScenarioNode, UUID> {

    List<ScenarioNode> findByScenarioId(UUID scenarioId);

    Optional<ScenarioNode> findByScenarioIdAndCode(UUID scenarioId, String code);
}
