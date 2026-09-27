package ru.vsm.backend.gamification.award.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.award.domain.CustomAward;

public interface CustomAwardRepository extends JpaRepository<CustomAward, UUID> {

    boolean existsByCode(String code);
}
