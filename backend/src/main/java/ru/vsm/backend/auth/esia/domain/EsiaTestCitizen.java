package ru.vsm.backend.auth.esia.domain;

import java.util.UUID;

/** Тестовый гражданин демо-заглушки Госуслуг/ЕСИА — фиксированный id, чтобы повторный вход тем же */
public record EsiaTestCitizen(UUID id, String code, String fullName, String snilsMasked) {
}
