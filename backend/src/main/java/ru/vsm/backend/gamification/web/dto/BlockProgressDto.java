package ru.vsm.backend.gamification.web.dto;

/** Мини-прогресс по одному блоку ситуаций — см. design/screens/profile.md, п.2. */
public record BlockProgressDto(
        String block,
        int scenariosCompleted,
        int loyaltyPoints,
        int safetyPoints) {
}
