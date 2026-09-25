package ru.vsm.backend.feedback.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import ru.vsm.backend.feedback.dto.RoleStep;
import ru.vsm.backend.scenario.domain.NodeType;
import ru.vsm.backend.scenario.domain.RoleStepFlags;
import ru.vsm.backend.scenario.domain.ScenarioChoice;

/**
 * Резолвит человекочитаемое объяснение "что пошло не так и почему" / "что сделано верно" для
 * одного выбора в разборе прохождения.
 *
 * <p>Приоритет источников (как просила задача): 1) явный текст пояснения по
 * {@link ScenarioChoice#getExplanationKey()} из {@link #EXPLANATION_TEXTS} — точка расширения на
 * будущее, когда контент-редактор сценариев начнёт заполнять тексты пояснений по ключу;
 * 2) если явного текста нет (сейчас всегда, ни один seed-файл его пока не содержит) —
 * алгоритмический fallback по разметке шагов ролевой модели ({@link RoleStepFlags}), знаку дельт
 * шкал (конфликт шкал = осознанный компромисс) и норме из {@code dataset/standards/}, если
 * нарушена (см. {@link #SAFETY_NORM_BY_SCENARIO_CODE}).
 */
@Component
public class ExplanationResolver {

    /** Точка расширения: explanationKey -> готовый текст. Пока пуст — ни один seed не заполняет. */
    private static final Map<String, String> EXPLANATION_TEXTS = Map.of();

    /**
     * Норма из dataset/standards/, которая подтверждается/нарушается в этом сценарии, когда
     * рейтинг безопасности снижается ({@code safetyDelta < 0}). Ключ — {@code Scenario.code}.
     */
    private static final Map<String, String> SAFETY_NORM_BY_SCENARIO_CODE = Map.of(
            "boarding-no-ticket",
            "Нарушение норматива готовности поезда к посадке: без действительного билета "
                    + "посадка не допускается (dataset/standards/sto-rzd-03011-general.md, "
                    + "раздел «Применимость к нашему проекту»).",
            "medical-passenger-unwell",
            "Неотложные обращения (первая помощь) обязаны быть приоритетом обслуживания "
                    + "(dataset/standards/sto-rzd-03011-general.md, раздел «Требования к персоналу»).");

    /** Файл нормы для {@link #normReferences()} — тот же ключ, что и выше, отдельно ради краткости. */
    private static final String STANDARDS_GENERAL_FILE = "dataset/standards/sto-rzd-03011-general.md";

    /**
     * @param choice     выбор игрока
     * @param nodeType   тип узла, где сделан выбор (ESCALATION поясняется отдельно)
     * @param wasTimeout выбор применён автоматически по истечении таймера
     * @param scenarioCode код сценария (для резолва нормы)
     */
    public Explanation resolve(ScenarioChoice choice, NodeType nodeType, boolean wasTimeout, String scenarioCode) {
        String explicit = choice.getExplanationKey() == null ? null : EXPLANATION_TEXTS.get(choice.getExplanationKey());
        if (explicit != null) {
            return new Explanation(explicit, List.of());
        }
        return buildFallback(choice, nodeType, wasTimeout, scenarioCode);
    }

    private Explanation buildFallback(ScenarioChoice choice, NodeType nodeType, boolean wasTimeout, String scenarioCode) {
        StringBuilder text = new StringBuilder();
        List<String> normRefs = new ArrayList<>();

        if (wasTimeout) {
            text.append("Время на решение истекло — выбор применён автоматически. ");
        }
        if (nodeType == NodeType.ESCALATION) {
            text.append("Узел эскалации: решение принято при участии начальника поезда/службы. ");
        }

        List<RoleStep> completed = completedSteps(choice.getRoleSteps());
        List<RoleStep> skipped = skippedSteps(choice.getRoleSteps());
        if (completed.size() == 4) {
            text.append("Все 4 шага ролевой модели соблюдены: признание, правило, решение, заверение. ");
        } else if (!skipped.isEmpty()) {
            text.append("Пропущен");
            text.append(skipped.size() > 1 ? "ы шаги ролевой модели: " : " шаг ролевой модели: ");
            text.append(skipped.stream()
                    .map(s -> "«" + s.label() + "» (например: " + s.examplePhrase() + ")")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(""));
            text.append(". ");
        }

        boolean scaleConflict = isScaleConflict(choice.getLoyaltyDelta(), choice.getSafetyDelta());
        if (scaleConflict) {
            if (choice.getSafetyDelta() > 0 && choice.getLoyaltyDelta() < 0) {
                text.append("Осознанный компромисс шкал: вежливый твёрдый отказ — безопасность выросла "
                        + "(+" + choice.getSafetyDelta() + "), лояльность просела (" + choice.getLoyaltyDelta() + "). ");
            } else if (choice.getSafetyDelta() < 0 && choice.getLoyaltyDelta() > 0) {
                text.append("Компромисс шкал не в пользу безопасности: лояльность выросла (+"
                        + choice.getLoyaltyDelta() + "), но безопасность просела (" + choice.getSafetyDelta() + "). ");
            }
        }

        if (choice.getSafetyDelta() < 0) {
            String norm = SAFETY_NORM_BY_SCENARIO_CODE.get(scenarioCode);
            if (norm != null) {
                text.append(norm).append(' ');
                normRefs.add(STANDARDS_GENERAL_FILE);
            }
        }

        if (text.isEmpty()) {
            text.append("Нейтральный шаг, без явного эффекта на разбор.");
        }
        return new Explanation(text.toString().trim(), normRefs);
    }

    public static boolean isScaleConflict(int loyaltyDelta, int safetyDelta) {
        return (loyaltyDelta > 0 && safetyDelta < 0) || (loyaltyDelta < 0 && safetyDelta > 0);
    }

    public static List<RoleStep> completedSteps(RoleStepFlags flags) {
        List<RoleStep> steps = new ArrayList<>();
        if (flags.isAcknowledge()) {
            steps.add(RoleStep.ACKNOWLEDGE);
        }
        if (flags.isRule()) {
            steps.add(RoleStep.RULE);
        }
        if (flags.isSolution()) {
            steps.add(RoleStep.SOLUTION);
        }
        if (flags.isReassure()) {
            steps.add(RoleStep.REASSURE);
        }
        return steps;
    }

    public static List<RoleStep> skippedSteps(RoleStepFlags flags) {
        List<RoleStep> steps = new ArrayList<>();
        if (!flags.isAcknowledge()) {
            steps.add(RoleStep.ACKNOWLEDGE);
        }
        if (!flags.isRule()) {
            steps.add(RoleStep.RULE);
        }
        if (!flags.isSolution()) {
            steps.add(RoleStep.SOLUTION);
        }
        if (!flags.isReassure()) {
            steps.add(RoleStep.REASSURE);
        }
        return steps;
    }

    /** Результат резолва: текст пояснения + ссылки на файлы норм (пусто, если норма не затронута). */
    public record Explanation(String text, List<String> normReferences) {
    }
}
