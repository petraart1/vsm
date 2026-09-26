package ru.vsm.backend.scenario.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.domain.ScenarioNodePortrait;

public interface ScenarioNodePortraitRepository extends JpaRepository<ScenarioNodePortrait, UUID> {

    List<ScenarioNodePortrait> findByNodeId(UUID nodeId);

    Optional<ScenarioNodePortrait> findByNodeIdAndCarClass(UUID nodeId, CarClass carClass);
}
