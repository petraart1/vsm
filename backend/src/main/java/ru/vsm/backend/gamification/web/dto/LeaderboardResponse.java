package ru.vsm.backend.gamification.web.dto;

import java.util.List;

/**
 * Ответ GET /api/gamification/leaderboard — см. design/screens/leaderboard.md.
 *
 * <p>{@code me} — закреплённая карточка "Ваше место" (может быть вне {@code top}), заполняется
 * только если запрашивающего можно опознать (валидный {@code Authorization: Bearer} или заголовок
 * {@code X-Player-Id}) и его профиль существует; иначе {@code null} (design: "ещё не участвует").
 * Личность запрашивающего больше не передаётся клиентом явным query-параметром — так публичный
 * лидерборд нельзя использовать, чтобы подсунуть чужой id и получить его строку помеченной как
 * "моя" (см. {@code ru.vsm.backend.auth.security.PlayerAccessGuard}).
 */
public record LeaderboardResponse(List<LeaderboardEntryDto> top, LeaderboardEntryDto me) {
}
