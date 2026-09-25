package ru.vsm.backend.scenario.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.scenario.domain.ScenarioChoice;

public interface ScenarioChoiceRepository extends JpaRepository<ScenarioChoice, UUID> {

    List<ScenarioChoice> findByNodeIdOrderBySortOrder(UUID nodeId);
}
