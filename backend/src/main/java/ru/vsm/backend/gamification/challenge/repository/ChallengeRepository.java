package ru.vsm.backend.gamification.challenge.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.gamification.challenge.domain.Challenge;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    Optional<Challenge> findByCode(String code);

    /** Челленджи, чей период действия покрывает {@code at} (обе границы включительно). */
    List<Challenge> findByStartsAtLessThanEqualAndEndsAtGreaterThanEqual(Instant startsAtReference,
            Instant endsAtReference);
}
