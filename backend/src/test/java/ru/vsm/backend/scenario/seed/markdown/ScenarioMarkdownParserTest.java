package ru.vsm.backend.scenario.seed.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.vsm.backend.scenario.domain.NodeType;
import ru.vsm.backend.scenario.seed.ChoiceSeedDto;
import ru.vsm.backend.scenario.seed.NodeSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;
import ru.vsm.backend.scenario.service.exception.MarkdownImportException;

/** Юнит-тесты {@link ScenarioMarkdownParser}, без Spring-контекста. */
class ScenarioMarkdownParserTest {

    private final ScenarioMarkdownParser parser = new ScenarioMarkdownParser();

    @Test
    void parsesFullGrammarIntoSeedDto() {
        String markdown = """
                # Тестовая ситуация
                Код: unit-test-scenario
                Блок: safety
                Описание: краткое описание
                Флагман: да
                Версия: 2
                Начальный узел: start

                ## start
                Тип: ESCALATION
                Скрыт от пассажира: да
                Реплика пассажира, возможно
                на нескольких строках.

                - [go] Первый вариант -> end (лояльность +3, безопасность -4)
                > Пояснение: пояснение варианта.
                > Норма: dataset/standards/example.md
                > Шаги: признать, правило, решение, заверить

                - [stay] Второй вариант без цели (лояльность -1, безопасность +2)

                Таймер: 15 с, по умолчанию: go

                ## end
                Итог узла.
                Итог: PARTIAL
                """;

        ScenarioSeedDto dto = parser.parse(markdown);

        assertThat(dto.getCode()).isEqualTo("unit-test-scenario");
        assertThat(dto.getBlock()).isEqualTo("safety");
        assertThat(dto.getDescription()).isEqualTo("краткое описание");
        assertThat(dto.isFlagship()).isTrue();
        assertThat(dto.getVersion()).isEqualTo(2);
        assertThat(dto.getEntryNode()).isEqualTo("start");
        assertThat(dto.getNodes()).hasSize(2);

        NodeSeedDto start = dto.getNodes().get(0);
        assertThat(start.getCode()).isEqualTo("start");
        assertThat(start.getType()).isEqualTo(NodeType.ESCALATION.name());
        assertThat(start.isHiddenFromPassenger()).isTrue();
        assertThat(start.getText()).isEqualTo("Реплика пассажира, возможно на нескольких строках.");
        assertThat(start.getTimerSeconds()).isEqualTo(15);
        assertThat(start.getDefaultChoice()).isEqualTo("go");
        assertThat(start.getChoices()).hasSize(2);

        ChoiceSeedDto go = start.getChoices().get(0);
        assertThat(go.getText()).isEqualTo("Первый вариант");
        assertThat(go.getTarget()).isEqualTo("end");
        assertThat(go.getLoyaltyDelta()).isEqualTo(3);
        assertThat(go.getSafetyDelta()).isEqualTo(-4);
        assertThat(go.getExplanation()).isEqualTo("пояснение варианта.");
        assertThat(go.getNormRef()).isEqualTo("dataset/standards/example.md");
        assertThat(go.getRoleSteps().isAcknowledge()).isTrue();
        assertThat(go.getRoleSteps().isRule()).isTrue();
        assertThat(go.getRoleSteps().isSolution()).isTrue();
        assertThat(go.getRoleSteps().isReassure()).isTrue();

        ChoiceSeedDto stay = start.getChoices().get(1);
        assertThat(stay.getText()).isEqualTo("Второй вариант без цели");
        assertThat(stay.getTarget()).isNull();

        NodeSeedDto end = dto.getNodes().get(1);
        assertThat(end.isTerminal()).isTrue();
        assertThat(end.getType()).isEqualTo(NodeType.TERMINAL.name());
        assertThat(end.getTerminalOutcome()).isEqualTo("PARTIAL");
        assertThat(end.getText()).isEqualTo("Итог узла.");
        assertThat(end.getOutcomeSummary()).isEqualTo("Итог узла.");
    }

    @Test
    void missingRequiredMetadataAndBrokenChoiceLineAreCollectedTogether() {
        String markdown = """
                # Без кода и блока
                ## start
                Текст узла.

                - [bad] Вариант без дельт -> target
                """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(MarkdownImportException.class)
                .satisfies(e -> {
                    MarkdownImportException mie = (MarkdownImportException) e;
                    assertThat(mie.getErrors()).anyMatch(msg -> msg.contains("Код"));
                    assertThat(mie.getErrors()).anyMatch(msg -> msg.contains("Блок"));
                    assertThat(mie.getErrors()).anyMatch(msg -> msg.contains("лояльность"));
                });
    }

    @Test
    void unknownRoleStepWordIsReportedAsError() {
        String markdown = """
                # Ситуация
                Код: unit-test-role-step
                Блок: misc

                ## start
                Текст.

                - [go] Вариант -> end (лояльность +1, безопасность +1)
                > Шаги: признать, летать

                ## end
                Итог.
                Итог: SUCCESS
                """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(MarkdownImportException.class)
                .satisfies(e -> assertThat(((MarkdownImportException) e).getErrors())
                        .anyMatch(msg -> msg.contains("летать")));
    }

    @Test
    void duplicateNodeCodeIsReportedAsError() {
        String markdown = """
                # Дубликат
                Код: unit-test-duplicate
                Блок: misc

                ## start
                Текст.
                Итог: SUCCESS

                ## start
                Другой текст.
                Итог: FAILURE
                """;

        assertThatThrownBy(() -> parser.parse(markdown))
                .isInstanceOf(MarkdownImportException.class)
                .satisfies(e -> assertThat(((MarkdownImportException) e).getErrors())
                        .anyMatch(msg -> msg.contains("повторяется")));
    }
}
