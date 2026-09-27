package ru.vsm.backend.gamification.expiry.web.dto;

/**
 * Итог одного прогона {@code PointsExpiryService#run} — сколько игроков предупреждено и у скольких
 * реально сгорели баллы, плюс суммарное число сгоревших баллов (для админ-эндпоинта демо-запуска
 * и для лога планового запуска).
 */
public record PointsExpiryRunResult(int warnedPlayers, int expiredPlayers, int totalPointsExpired) {
}
