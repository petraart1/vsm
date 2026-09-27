package ru.vsm.backend.gamification.challenge.web.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Тело {@code POST /api/admin/challenges} — событие (челлендж), созданное администратором. */
public record AdminChallengeRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 500) String description,
        @NotNull @Pattern(regexp = "BLOCK_SCENARIOS_NO_FAILURE|SAFETY_STREAK|ROLE_MODEL_ALL_STEPS") String goalType,
        @Size(max = 32) String targetBlock,
        @Min(1) @Max(100) int targetCount,
        @Min(0) @Max(100) Integer safetyThreshold,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Min(0) @Max(5000) int rewardPoints,
        @Size(max = 64) String rewardAchievementCode) {
}
