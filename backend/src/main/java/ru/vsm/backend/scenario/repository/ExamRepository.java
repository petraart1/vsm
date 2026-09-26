package ru.vsm.backend.scenario.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.scenario.domain.Exam;

public interface ExamRepository extends JpaRepository<Exam, UUID> {
}
