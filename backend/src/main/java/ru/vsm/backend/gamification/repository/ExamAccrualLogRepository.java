package ru.vsm.backend.gamification.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.domain.ExamAccrualLogEntry;

public interface ExamAccrualLogRepository extends JpaRepository<ExamAccrualLogEntry, UUID> {

    boolean existsByExamId(UUID examId);
}
