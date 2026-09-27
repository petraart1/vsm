package ru.vsm.backend.gamification.web.dto;

import java.util.List;

/** Ответ GET /api/gamification/leaderboard — см. design/screens/leaderboard.md. */
public record LeaderboardResponse(List<LeaderboardEntryDto> top, LeaderboardEntryDto me) {
}
