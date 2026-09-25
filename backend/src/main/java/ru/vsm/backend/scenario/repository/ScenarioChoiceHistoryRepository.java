package ru.vsm.backend.scenario.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.scenario.domain.ScenarioChoiceHistory;

public interface ScenarioChoiceHistoryRepository extends JpaRepository<ScenarioChoiceHistory, UUID> {

    List<ScenarioChoiceHistory> findByUserProgressIdOrderBySequenceIndex(UUID userProgressId);

    long countByUserProgressId(UUID userProgressId);
}
