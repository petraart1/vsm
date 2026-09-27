package ru.vsm.backend.scenario.web.dto;

/** Тело {@code PATCH /api/admin/scenarios/{code}} — включить/выключить сценарий в каталоге. */
public record AdminScenarioPatchRequest(Boolean active) {
}
