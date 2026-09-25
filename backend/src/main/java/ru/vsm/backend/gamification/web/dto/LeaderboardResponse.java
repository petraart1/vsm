package ru.vsm.backend.gamification.web.dto;

import java.util.List;

/**
 * Ответ GET /api/gamification/leaderboard — см. design/screens/leaderboard.md.
 *
 * <p>{@code me} — закреплённая карточка "Ваше место", заполняется только если передан
 * {@code X-User-Id} и профиль игрока существует; иначе null (design: "ещё не участвует").
 */
public record LeaderboardResponse(List<LeaderboardEntryDto> top, LeaderboardEntryDto me) {
}
