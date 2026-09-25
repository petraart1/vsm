package ru.vsm.backend.gamification.web.dto;

import java.util.UUID;

public record LeaderboardEntryDto(
        long rank,
        UUID playerId,
        String displayName,
        int totalScore,
        int scenariosCompleted) {
}
