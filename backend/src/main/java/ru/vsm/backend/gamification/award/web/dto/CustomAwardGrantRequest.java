package ru.vsm.backend.gamification.award.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Тело {@code POST /api/admin/awards/{id}/grant}: логин учётной записи или playerId (UUID). */
public record CustomAwardGrantRequest(@NotBlank @Size(max = 64) String player) {
}
