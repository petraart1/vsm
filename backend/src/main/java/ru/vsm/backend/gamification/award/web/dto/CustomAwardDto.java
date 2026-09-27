package ru.vsm.backend.gamification.award.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Награда администратора. {@code earned}/{@code earnedAt} заполнены для каталога конкретного
 * игрока; {@code grantedCount} — только в админ-списке.
 */
public record CustomAwardDto(
        UUID id,
        String code,
        String title,
        String description,
        String shape,
        String glyph,
        boolean verifiedOnly,
        boolean earned,
        Instant earnedAt,
        long grantedCount) {
}
