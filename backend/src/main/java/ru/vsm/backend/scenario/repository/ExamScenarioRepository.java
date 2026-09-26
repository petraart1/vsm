package ru.vsm.backend.scenario.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.scenario.domain.ExamScenario;

public interface ExamScenarioRepository extends JpaRepository<ExamScenario, UUID> {

    List<ExamScenario> findByExamIdOrderBySortOrder(UUID examId);

    Optional<ExamScenario> findByExamIdAndScenarioId(UUID examId, UUID scenarioId);
}
