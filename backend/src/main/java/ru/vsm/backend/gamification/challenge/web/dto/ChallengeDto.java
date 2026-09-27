package ru.vsm.backend.gamification.challenge.web.dto;

import java.time.Instant;

/** Активный челлендж месяца с прогрессом конкретного игрока (или нулевым прогрессом, если игрок */
public record ChallengeDto(
        String code,
        String title,
        String description,
        String goalType,
        String targetBlock,
        int targetCount,
        Integer safetyThreshold,
        int rewardPoints,
        Instant startsAt,
        Instant endsAt,
        int current,
        boolean completed,
        Instant completedAt) {
}
