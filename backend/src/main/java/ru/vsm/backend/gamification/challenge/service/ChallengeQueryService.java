package ru.vsm.backend.gamification.challenge.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.gamification.challenge.domain.Challenge;
import ru.vsm.backend.gamification.challenge.domain.ChallengeProgress;
import ru.vsm.backend.gamification.challenge.repository.ChallengeProgressRepository;
import ru.vsm.backend.gamification.challenge.repository.ChallengeRepository;
import ru.vsm.backend.gamification.challenge.web.dto.ChallengeDto;

/**
 * Чтение активных челленджей месяца с прогрессом игрока для REST-контроллера. Обновление
 * прогресса — только в {@code GamificationAccrualService} по {@code ScenarioCompletedEvent}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChallengeQueryService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeProgressRepository challengeProgressRepository;

    /**
     * Челленджи, чей период действия покрывает текущий момент. Без {@code playerId} прогресс у
     * всех — {@code current=0}, {@code completed=false} (просмотр каталога без привязки к игроку).
     */
    public List<ChallengeDto> getActiveChallenges(UUID playerId) {
        Instant now = Instant.now();
        List<Challenge> active =
                challengeRepository.findByStartsAtLessThanEqualAndEndsAtGreaterThanEqual(now, now);

        Map<UUID, ChallengeProgress> progressByChallengeId = playerId == null
                ? Map.of()
                : challengeProgressRepository.findByPlayerId(playerId).stream()
                        .collect(Collectors.toMap(ChallengeProgress::getChallengeId, p -> p));

        return active.stream()
                .sorted(Comparator.comparing(Challenge::getCode))
                .map(challenge -> toDto(challenge, progressByChallengeId.get(challenge.getId())))
                .toList();
    }

    private ChallengeDto toDto(Challenge challenge, ChallengeProgress progress) {
        int current = progress != null ? progress.getCurrentValue() : 0;
        boolean completed = progress != null && progress.isCompleted();
        Instant completedAt = progress != null ? progress.getCompletedAt() : null;
        return new ChallengeDto(
                challenge.getCode(),
                challenge.getTitle(),
                challenge.getDescription(),
                challenge.getGoalType().name(),
                challenge.getTargetBlock(),
                challenge.getTargetCount(),
                challenge.getSafetyThreshold(),
                challenge.getRewardPoints(),
                challenge.getStartsAt(),
                challenge.getEndsAt(),
                current,
                completed,
                completedAt);
    }
}
