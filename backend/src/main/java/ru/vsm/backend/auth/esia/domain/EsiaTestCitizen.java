package ru.vsm.backend.auth.esia.domain;

import java.util.UUID;

/**
 * Тестовый гражданин демо-заглушки Госуслуг/ЕСИА — фиксированный id, чтобы повторный вход тем же
 * гражданином находил ту же учётную запись, а не создавал новую.
 *
 * @param code короткий слаг, используется в ссылках выбора и как основа синтетических login/email
 * @param snilsMasked СНИЛС в маскированном виде — только для отображения на демо-странице
 */
public record EsiaTestCitizen(UUID id, String code, String fullName, String snilsMasked) {
}
