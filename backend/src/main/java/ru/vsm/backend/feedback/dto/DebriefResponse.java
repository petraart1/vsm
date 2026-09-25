package ru.vsm.backend.feedback.dto;

import java.util.List;
import java.util.UUID;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;

/**
 * Разбор одного прохождения сценария (см. {@code design/screens/debrief.md}). Собирается на
 * лету из {@code scenario_choice_history} и графа сценария — своих таблиц для MVP нет.
 *
 * <p>Блок "Начисления" (очки/ачивки/лидерборд, design-раздел 4) в этот DTO не входит — это
 * ответственность домена gamification, отдельный вызов на фронтенде.
 *
 * @param userProgressId    id прохождения
 * @param scenarioId        id сценария
 * @param scenarioCode      код сценария
 * @param scenarioTitle     заголовок сценария
 * @param scenarioBlock     блок датасета (boarding/medical/...)
 * @param progressStatus    статус прохождения (IN_PROGRESS/COMPLETED/ABANDONED)
 * @param outcome           итоговый исход (null, если прохождение ещё не завершено)
 * @param verdict           текстовый вердикт для заголовка экрана
 * @param interrupted       true, если {@code progressStatus == ABANDONED} — прохождение
 *                          завершилось не обычным терминальным узлом (см. "Пустые/ошибочные
 *                          состояния" в design/screens/debrief.md)
 * @param finalLoyaltyScore итоговое значение шкалы лояльности на момент разбора
 * @param finalSafetyScore  итоговое значение шкалы безопасности на момент разбора
 * @param timeline          пройденный путь: узел -> выбор -> эффект -> шаги ролевой модели
 * @param keyMoment         ключевая развилка с лучшей альтернативой ({@code null}, если
 *                          прохождение идеально — тогда смотри {@link #summary()})
 * @param summary           итоговый обучающий текст: похвала (идеальное прохождение) либо
 *                          разбор ключевой развилки на 2-4 предложения
 * @param normReferences    ссылки на нормы ({@link ru.vsm.backend.scenario.domain.ScenarioChoice#getNormRef()}
 *                          сделанных выборов, как заполнены в данных сценария — без выдуманных
 *                          ссылок для выборов, где норма не размечена), без дублей
 */
public record DebriefResponse(
        UUID userProgressId,
        UUID scenarioId,
        String scenarioCode,
        String scenarioTitle,
        String scenarioBlock,
        ProgressStatus progressStatus,
        ScenarioOutcome outcome,
        String verdict,
        boolean interrupted,
        int finalLoyaltyScore,
        int finalSafetyScore,
        List<DebriefStepDto> timeline,
        KeyMomentDto keyMoment,
        String summary,
        List<String> normReferences) {
}
