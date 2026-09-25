package ru.vsm.backend.scenario.seed;

import ru.vsm.backend.scenario.domain.NodeType;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;

/**
 * Шаблон простого сценария для {@code GET /api/editor/template} — отправная точка для новой
 * ситуации в редакторе: один вводный DIALOGUE-узел с тремя вариантами ответа, каждый ведёт в свой
 * терминальный узел (твёрдый отказ по норме / уступка с нарушением нормы / лучший вариант со всеми
 * шагами ролевой модели) — тот же паттерн, что у большинства простых (нефлагманских) сценариев
 * каталога. Возвращаемый объект — валидный {@link ScenarioSeedDto} (проходит
 * {@code ScenarioGraphValidator} как есть), но с плейсхолдерными кодом/текстами, которые нужно
 * заменить перед сохранением ({@code code} обязан быть уникален в каталоге).
 */
public final class ScenarioSeedTemplateFactory {

    private ScenarioSeedTemplateFactory() {
    }

    public static ScenarioSeedDto build() {
        ScenarioSeedDto dto = new ScenarioSeedDto();
        dto.setCode("new-scenario-template");
        dto.setSituationRef(null);
        dto.setBlock("misc");
        dto.setTitle("Новая ситуация (замените заголовок)");
        dto.setDescription("Краткое описание ситуации — что происходит и в чём сложность для проводника.");
        dto.setFlagship(false);
        dto.setVersion(1);
        dto.setEntryNode("start");

        NodeSeedDto start = new NodeSeedDto();
        start.setCode("start");
        start.setType(NodeType.DIALOGUE.name());
        start.setText("Реплика пассажира или описание ситуации, которую видит игрок.");
        start.getChoices().add(choice(
                "firm-by-the-book",
                "Твёрдо и вежливо объяснить правило, не нарушая норматив.",
                -2, 5, "resolved-by-the-book",
                true, true, true, false,
                "Правило соблюдено корректно, но полного разбора/заверения пассажира не хватает — есть куда расти.",
                0));
        start.getChoices().add(choice(
                "bend-the-rule",
                "Пойти навстречу пассажиру в обход правила.",
                10, -20, "resolved-with-violation",
                false, false, false, false,
                "Пассажир доволен в моменте, но это нарушение норматива — очки безопасности не начисляются"
                        + " за такой выбор.",
                1));
        start.getChoices().add(choice(
                "full-role-model",
                "Признать ситуацию, назвать правило, предложить решение и заверить пассажира.",
                5, 5, "resolved-well",
                true, true, true, true,
                "Все четыре шага ролевой модели закрыты — лучший вариант ответа.",
                2));
        dto.getNodes().add(start);

        dto.getNodes().add(terminal("resolved-by-the-book", ScenarioOutcome.PARTIAL,
                "Правило соблюдено, но коммуникация могла быть мягче."));
        dto.getNodes().add(terminal("resolved-with-violation", ScenarioOutcome.FAILURE,
                "Норматив нарушен ради сиюминутной лояльности."));
        dto.getNodes().add(terminal("resolved-well", ScenarioOutcome.SUCCESS,
                "Образцовое решение — норматив соблюдён, пассажир доволен."));
        return dto;
    }

    private static ChoiceSeedDto choice(
            String code, String text, int loyaltyDelta, int safetyDelta, String target,
            boolean acknowledge, boolean rule, boolean solution, boolean reassure,
            String explanation, int sortOrder) {
        ChoiceSeedDto c = new ChoiceSeedDto();
        c.setCode(code);
        c.setText(text);
        c.setLoyaltyDelta(loyaltyDelta);
        c.setSafetyDelta(safetyDelta);
        c.setTarget(target);
        RoleStepsSeedDto roleSteps = new RoleStepsSeedDto();
        roleSteps.setAcknowledge(acknowledge);
        roleSteps.setRule(rule);
        roleSteps.setSolution(solution);
        roleSteps.setReassure(reassure);
        c.setRoleSteps(roleSteps);
        c.setExplanationKey(null);
        c.setExplanation(explanation);
        c.setNormRef(null);
        c.setSortOrder(sortOrder);
        return c;
    }

    private static NodeSeedDto terminal(String code, ScenarioOutcome outcome, String outcomeSummary) {
        NodeSeedDto n = new NodeSeedDto();
        n.setCode(code);
        n.setType(NodeType.TERMINAL.name());
        n.setText(outcomeSummary);
        n.setTerminal(true);
        n.setTerminalOutcome(outcome.name());
        n.setOutcomeSummary(outcomeSummary);
        return n;
    }
}
