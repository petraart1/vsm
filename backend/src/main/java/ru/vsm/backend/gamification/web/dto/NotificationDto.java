package ru.vsm.backend.gamification.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Элемент списка уведомлений — см. GET /api/gamification/notifications. */
public record NotificationDto(
        UUID id,
        String type,
        String title,
        String body,
        Instant createdAt,
        Instant readAt) {

    public boolean unread() {
        return readAt == null;
    }
}
