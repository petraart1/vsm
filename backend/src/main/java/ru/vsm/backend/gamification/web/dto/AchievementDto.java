package ru.vsm.backend.gamification.web.dto;

import java.time.Instant;

/**
 * Одна ачивка из каталога, с состоянием для конкретного игрока (либо "не определён", если
 * запрос анонимный — тогда все ачивки locked). См. design/screens/achievements.md.
 */
public record AchievementDto(
        String code,
        String title,
        String description,
        String category,
        boolean earned,
        Instant earnedAt) {
}
