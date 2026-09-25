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
 * <p>Приоритет источников: 1) авторский текст {@link ScenarioChoice#getExplanation()}, заполняемый
 * сценаристом прямо в seed-данных выбора — приоритетнее всего остального, ничего не достраивается
 * поверх; 2) явный текст по {@link ScenarioChoice#getExplanationKey()} из {@link #EXPLANATION_TEXTS}
 * — точка расширения на будущее (общие формулировки на несколько выборов сразу, по ключу, без
 * дублирования текста в каждом seed-файле); 3) если ни того, ни другого нет — алгоритмический
 * fallback по разметке шагов ролевой модели ({@link RoleStepFlags}) и знаку дельт шкал (конфликт
 * шкал = осознанный компромисс).
 *
 * <p>Ссылка на норму ({@link ScenarioChoice#getNormRef()}) берётся напрямую из данных выбора,
 * независимо от того, какой из трёх источников дал текст объяснения выше — никакой привязки к
 * коду сценария и никакого домысливания нормы, если поле не заполнено: seed без {@code normRef}
 * даёт {@link Explanation#normReferences()} пустым.
 */
@Component
public class ExplanationResolver {

    /** Точка расширения: explanationKey -> готовый текст (общие формулировки без текста в seed). */
    private static final Map<String, String> EXPLANATION_TEXTS = Map.of();

    /**
     * @param choice     выбор игрока
     * @param nodeType   тип узла, где сделан выбор (ESCALATION поясняется отдельно в fallback-ветке)
     * @param wasTimeout выбор применён автоматически по истечении таймера
     */
    public Explanation resolve(ScenarioChoice choice, NodeType nodeType, boolean wasTimeout) {
        List<String> normRefs = choice.getNormRef() == null || choice.getNormRef().isBlank()
                ? List.of()
                : List.of(choice.getNormRef());

        if (choice.getExplanation() != null && !choice.getExplanation().isBlank()) {
            return new Explanation(choice.getExplanation().trim(), normRefs);
        }

        String mapped = choice.getExplanationKey() == null ? null : EXPLANATION_TEXTS.get(choice.getExplanationKey());
        if (mapped != null) {
            return new Explanation(mapped, normRefs);
        }

        return buildFallback(choice, nodeType, wasTimeout, normRefs);
    }

    private Explanation buildFallback(ScenarioChoice choice, NodeType nodeType, boolean wasTimeout, List<String> normRefs) {
        StringBuilder text = new StringBuilder();

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

    /** Результат резолва: текст пояснения + ссылки на нормы (пусто, если норма не заявлена в данных). */
    public record Explanation(String text, List<String> normReferences) {
    }
}
