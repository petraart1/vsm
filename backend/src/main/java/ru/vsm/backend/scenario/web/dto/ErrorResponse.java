package ru.vsm.backend.scenario.web.dto;

/** Единый формат тела ошибки для REST API прохождения сценариев. */
public record ErrorResponse(String error, String message) {
}
