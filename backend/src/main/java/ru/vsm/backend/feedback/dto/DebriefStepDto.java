package ru.vsm.backend.feedback.dto;

import java.util.List;
import ru.vsm.backend.scenario.domain.NodeType;

/**
 * Один шаг таймлайна разбора прохождения (см. {@code design/screens/debrief.md}, раздел
 * "Пройденный путь"): реплика/ситуация узла -> выбор игрока -> эффект на шкалы -> какие шаги
 * ролевой модели соблюдены/пропущены -> объяснение.
 *
 * @param sequenceIndex        порядковый номер решения в прохождении (0, 1, 2, ...)
 * @param nodeCode             код узла сценария
 * @param nodeText             реплика/описание ситуации, которую видел игрок
 * @param nodeType             тип узла (DIALOGUE/ESCALATION/TERMINAL) — ESCALATION визуально
 *                             выделяется иконкой эскалации на фронтенде
 * @param choiceCode           код выбранного варианта
 * @param choiceText           текст выбранного варианта
 * @param wasTimeout           выбор применён автоматически по истечении таймера узла
 * @param loyaltyDelta         фактически применённая дельта шкалы лояльности
 * @param safetyDelta          фактически применённая дельта шкалы безопасности
 * @param roleStepsCompleted   какие шаги ролевой модели соблюдены этим выбором (подписи)
 * @param roleStepsSkipped     какие шаги ролевой модели пропущены этим выбором (подписи)
 * @param scaleConflict        true, если выбор — осознанный компромисс шкал (дельты разного знака)
 * @param explanation          человекочитаемое объяснение «что пошло не так и почему» / что было
 *                             сделано верно для этого шага
 * @param hiddenCommunicationEffect true, если решение принято в закадровом узле (см.
 *                             {@link ru.vsm.backend.scenario.domain.ScenarioNode#isHiddenFromPassenger()}) —
 *                             пассажир сам разговор не слышит, эффект только на шкалы. Для узлов
 *                             без явного флага используется резервная эвристика: узел-эскалация,
 *                             где все альтернативы дают одну и ту же дельту лояльности, но
 *                             расходятся по эффекту на безопасность.
 * @param normRef              ссылка на норматив у сделанного выбора ({@code null}, если автор
 *                             seed-данных её не указал) — то же значение, что и элемент общего
 *                             {@code DebriefResponse#normReferences()} для этого шага, продублировано
 *                             на уровне шага для клиентов, которым нужна привязка нормы к конкретному
 *                             решению, а не только общий список по всему прохождению
 */
public record DebriefStepDto(
        int sequenceIndex,
        String nodeCode,
        String nodeText,
        NodeType nodeType,
        String choiceCode,
        String choiceText,
        boolean wasTimeout,
        int loyaltyDelta,
        int safetyDelta,
        List<String> roleStepsCompleted,
        List<String> roleStepsSkipped,
        boolean scaleConflict,
        String explanation,
        boolean hiddenCommunicationEffect,
        String normRef) {
}
