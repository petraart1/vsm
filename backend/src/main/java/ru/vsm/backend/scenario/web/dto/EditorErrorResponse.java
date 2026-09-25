package ru.vsm.backend.scenario.web.dto;

import java.util.List;

/**
 * Тело ошибки редактора сценариев для {@code /api/editor/scenarios}: в отличие от
 * {@link ErrorResponse} (одно сообщение) здесь список проблем графа — узел/выбор/что не так,
 * все сразу, не по одной за запрос.
 */
public record EditorErrorResponse(String error, List<String> messages) {
}
