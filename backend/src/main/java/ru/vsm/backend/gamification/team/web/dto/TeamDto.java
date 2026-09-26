package ru.vsm.backend.gamification.team.web.dto;

import java.util.UUID;

/** Элемент списка команд — GET /api/gamification/teams и ответ POST .../join. */
public record TeamDto(UUID id, String code, String name, String depot, long memberCount) {
}
