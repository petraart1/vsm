package ru.vsm.backend.gamification.challenge.web.admin;

import java.time.Instant;
import java.util.UUID;
import ru.vsm.backend.gamification.challenge.domain.Challenge;

/** Челлендж в админ-панели: полный набор полей без прогресса конкретного игрока. */
public record AdminChallengeDto(
        UUID id,
        String code,
        String title,
        String description,
        String goalType,
        String targetBlock,
        int targetCount,
        Integer safetyThreshold,
        Instant startsAt,
        Instant endsAt,
        int rewardPoints,
        String rewardAchievementCode,
        boolean active) {

    public static AdminChallengeDto from(Challenge c, Instant now) {
        return new AdminChallengeDto(c.getId(), c.getCode(), c.getTitle(), c.getDescription(),
                c.getGoalType().name(), c.getTargetBlock(), c.getTargetCount(), c.getSafetyThreshold(),
                c.getStartsAt(), c.getEndsAt(), c.getRewardPoints(), c.getRewardAchievementCode(),
                !now.isBefore(c.getStartsAt()) && !now.isAfter(c.getEndsAt()));
    }
}
