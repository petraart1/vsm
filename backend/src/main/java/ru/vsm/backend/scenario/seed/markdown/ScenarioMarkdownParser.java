package ru.vsm.backend.scenario.seed.markdown;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import ru.vsm.backend.scenario.domain.NodeType;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.seed.ChoiceSeedDto;
import ru.vsm.backend.scenario.seed.NodeSeedDto;
import ru.vsm.backend.scenario.seed.RoleStepsSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;
import ru.vsm.backend.scenario.service.exception.MarkdownImportException;

/**
 * Разбирает простой построчный markdown-формат ситуации в {@link ScenarioSeedDto} — тот же
 * seed-формат, что принимает {@code POST /api/editor/scenarios}, только человекочитаемым текстом
 * вместо JSON. Формат и пример — README, раздел «Редактор сценариев» / «Импорт из markdown».
 *
 * <p>Грамматика (построчно, пустые строки — только разделители, не значимы):
 * <pre>
 * # Заголовок ситуации
 * Код: my-scenario-code
 * Блок: safety
 * Описание: краткое описание (опционально)
 * Флагман: да|нет (опционально, по умолчанию нет)
 * Версия: 1 (опционально, по умолчанию 1)
 * Начальный узел: start (опционально, по умолчанию — код первого узла)
 *
 * ## start
 * Тип: ESCALATION (опционально, по умолчанию DIALOGUE; игнорируется, если ниже есть "Итог:")
 * Скрыт от пассажира: да (опционально, по умолчанию нет)
 * Текст узла — реплика или описание ситуации, одна или несколько строк.
 *
 * - [код-варианта] текст варианта -&gt; целевой-узел (лояльность +N, безопасность -M)
 * &gt; Пояснение: почему этот вариант хорош/плох (опционально)
 * &gt; Норма: ссылка на норматив (опционально)
 * &gt; Шаги: признать, правило, решение, заверить (опционально, любое подмножество)
 *
 * Таймер: 30 с, по умолчанию: код-варианта (опционально, только у узлов с вариантами)
 *
 * ## terminal-node
 * Текст терминального узла.
 * Итог: SUCCESS|PARTIAL|FAILURE
 * </pre>
 *
 * <p>Не поддерживает «портрет пассажира» ({@code passengerPortraits}) — для этого нужен обычный
 * JSON-формат через {@code POST /api/editor/scenarios} или экспорт {@code GET
 * /api/editor/scenarios/{code}}.
 *
 * <p>Ошибки разметки (строка не распознана, обязательные метаданные отсутствуют, некорректный
 * enum) собираются все сразу и бросаются одним {@link MarkdownImportException} — вызывающий код
 * получает полный список проблем за один проход, а не по одной ошибке за запрос. Ссылки между
 * узлами ({@code target}/{@code entryNode}/достижимость) здесь не проверяются — это задача
 * {@code ScenarioGraphValidator}, вызываемого после успешного разбора.
 */
@Component
public class ScenarioMarkdownParser {

    private static final Pattern TITLE = Pattern.compile("^#\\s+(.+)$");
    private static final Pattern CODE_LINE = Pattern.compile("^Код:\\s*(\\S+)\\s*$");
    private static final Pattern BLOCK_LINE = Pattern.compile("^Блок:\\s*(.+)$");
    private static final Pattern DESCRIPTION_LINE = Pattern.compile("^Описание:\\s*(.+)$");
    private static final int CYRILLIC_CASE_INSENSITIVE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern FLAGSHIP_LINE = Pattern.compile("^Флагман:\\s*(да|нет)\\s*$", CYRILLIC_CASE_INSENSITIVE);
    private static final Pattern VERSION_LINE = Pattern.compile("^Версия:\\s*(\\d+)\\s*$");
    private static final Pattern ENTRY_NODE_LINE = Pattern.compile("^Начальный узел:\\s*(\\S+)\\s*$");
    private static final Pattern TYPE_LINE = Pattern.compile("^Тип:\\s*(\\S+)\\s*$");
    private static final Pattern HIDDEN_LINE =
            Pattern.compile("^Скрыт от пассажира:\\s*(да|нет)\\s*$", CYRILLIC_CASE_INSENSITIVE);
    private static final Pattern CHOICE_LINE = Pattern.compile("^-\\s*\\[([^\\]]+)]\\s*(.+)$");
    private static final Pattern DELTA_TAIL = Pattern.compile(
            "^(.*?)\\(\\s*лояльность\\s*([+-]?\\d+)\\s*,\\s*безопасность\\s*([+-]?\\d+)\\s*\\)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_TARGET = Pattern.compile("^(.+?)\\s*->\\s*(\\S+)\\s*$");
    private static final Pattern EXPLANATION_LINE = Pattern.compile("^>\\s*Пояснение:\\s*(.+)$");
    private static final Pattern NORM_LINE = Pattern.compile("^>\\s*Норма:\\s*(.+)$");
    private static final Pattern STEPS_LINE = Pattern.compile("^>\\s*Шаги:\\s*(.+)$");
    private static final Pattern TIMER_LINE =
            Pattern.compile("^Таймер:\\s*(\\d+)\\s*с\\s*,\\s*по умолчанию:\\s*(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OUTCOME_LINE = Pattern.compile("^Итог:\\s*(\\S+)\\s*$");

    public ScenarioSeedDto parse(String markdown) {
        List<String> errors = new ArrayList<>();
        ScenarioSeedDto dto = new ScenarioSeedDto();
        dto.setNodes(new ArrayList<>());
        Set<String> nodeCodes = new HashSet<>();

        String[] lines = markdown == null ? new String[0] : markdown.replace("\r\n", "\n").split("\n", -1);
        boolean titleSeen = false;
        boolean inHeader = true;
        NodeSeedDto currentNode = null;
        ChoiceSeedDto currentChoice = null;
        List<String> currentTextLines = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }

            if (!titleSeen) {
                Matcher m = TITLE.matcher(line);
                if (!m.matches()) {
                    errors.add(lineNo + ": ожидался заголовок вида '# Название ситуации' первой значимой строкой");
                } else {
                    dto.setTitle(m.group(1).strip());
                }
                titleSeen = true;
                continue;
            }

            if (inHeader && !line.startsWith("## ")) {
                if (!parseHeaderMetadataLine(line, dto)) {
                    errors.add(lineNo + ": нераспознанная строка метаданных сценария: '" + line + "'");
                }
                continue;
            }
            inHeader = false;

            if (line.startsWith("## ")) {
                finalizeChoice(currentNode, currentChoice);
                flushNodeText(currentNode, currentTextLines);
                if (currentNode != null) {
                    dto.getNodes().add(currentNode);
                }
                String code = line.substring(3).strip();
                if (code.isEmpty()) {
                    errors.add(lineNo + ": заголовок узла '## ' без кода");
                    code = "node-line-" + lineNo;
                } else if (!nodeCodes.add(code)) {
                    errors.add(lineNo + ": код узла '" + code + "' повторяется");
                }
                currentNode = new NodeSeedDto();
                currentNode.setCode(code);
                currentNode.setType(NodeType.DIALOGUE.name());
                currentTextLines = new ArrayList<>();
                currentChoice = null;
                continue;
            }

            if (currentNode == null) {
                errors.add(lineNo + ": строка вне узла (нет предшествующего '## код'): '" + line + "'");
                continue;
            }

            Matcher typeM = TYPE_LINE.matcher(line);
            if (typeM.matches()) {
                String type = typeM.group(1).toUpperCase();
                if (!isKnownNodeType(type)) {
                    errors.add(lineNo + ": некорректный 'Тип' узла '" + typeM.group(1)
                            + "' (ожидается DIALOGUE/ESCALATION/TERMINAL)");
                } else {
                    currentNode.setType(type);
                }
                continue;
            }

            Matcher hiddenM = HIDDEN_LINE.matcher(line);
            if (hiddenM.matches()) {
                currentNode.setHiddenFromPassenger(isYes(hiddenM.group(1)));
                continue;
            }

            Matcher choiceM = CHOICE_LINE.matcher(line);
            if (choiceM.matches()) {
                finalizeChoice(currentNode, currentChoice);
                flushNodeText(currentNode, currentTextLines);
                currentChoice = parseChoiceLine(choiceM, line, lineNo, errors);
                continue;
            }

            Matcher explM = EXPLANATION_LINE.matcher(line);
            if (explM.matches()) {
                if (currentChoice == null) {
                    errors.add(lineNo + ": '> Пояснение' встречено вне варианта ответа");
                } else {
                    currentChoice.setExplanation(explM.group(1).strip());
                }
                continue;
            }

            Matcher normM = NORM_LINE.matcher(line);
            if (normM.matches()) {
                if (currentChoice == null) {
                    errors.add(lineNo + ": '> Норма' встречено вне варианта ответа");
                } else {
                    currentChoice.setNormRef(normM.group(1).strip());
                }
                continue;
            }

            Matcher stepsM = STEPS_LINE.matcher(line);
            if (stepsM.matches()) {
                if (currentChoice == null) {
                    errors.add(lineNo + ": '> Шаги' встречено вне варианта ответа");
                } else {
                    currentChoice.setRoleSteps(parseRoleSteps(stepsM.group(1), lineNo, errors));
                }
                continue;
            }

            Matcher timerM = TIMER_LINE.matcher(line);
            if (timerM.matches()) {
                finalizeChoice(currentNode, currentChoice);
                currentChoice = null;
                int seconds = Integer.parseInt(timerM.group(1));
                String defaultCode = timerM.group(2).strip();
                currentNode.setTimerSeconds(seconds);
                currentNode.setDefaultChoice(defaultCode);
                boolean found = currentNode.getChoices().stream().anyMatch(c -> c.getCode().equals(defaultCode));
                if (!found) {
                    errors.add(lineNo + ": вариант по умолчанию '" + defaultCode
                            + "' не найден среди вариантов узла '" + currentNode.getCode() + "'");
                }
                continue;
            }

            Matcher outcomeM = OUTCOME_LINE.matcher(line);
            if (outcomeM.matches()) {
                finalizeChoice(currentNode, currentChoice);
                currentChoice = null;
                flushNodeText(currentNode, currentTextLines);
                String outcome = outcomeM.group(1).toUpperCase();
                if (!isKnownOutcome(outcome)) {
                    errors.add(lineNo + ": некорректный 'Итог' узла '" + outcomeM.group(1)
                            + "' (ожидается SUCCESS/PARTIAL/FAILURE)");
                } else {
                    currentNode.setTerminalOutcome(outcome);
                }
                currentNode.setTerminal(true);
                currentNode.setType(NodeType.TERMINAL.name());
                // Markdown не различает текст узла и резюме для разбора — используем один и тот
                // же абзац для обоих полей (в JSON seed-формате их можно развести отдельно).
                currentNode.setOutcomeSummary(currentNode.getText());
                continue;
            }

            if (currentChoice != null) {
                errors.add(lineNo + ": текстовая строка после начала вариантов узла '" + currentNode.getCode()
                        + "' (текст узла должен идти до первого '- [код] ...'): '" + line + "'");
                continue;
            }
            currentTextLines.add(line);
        }

        finalizeChoice(currentNode, currentChoice);
        flushNodeText(currentNode, currentTextLines);
        if (currentNode != null) {
            dto.getNodes().add(currentNode);
        }

        if (dto.getCode() == null || dto.getCode().isBlank()) {
            errors.add("не указана обязательная метаданные 'Код:' сценария");
        }
        if (dto.getBlock() == null || dto.getBlock().isBlank()) {
            errors.add("не указана обязательная метаданные 'Блок:' сценария");
        }
        if (dto.getNodes().isEmpty()) {
            errors.add("нет ни одного узла (заголовок вида '## код')");
        } else if (dto.getEntryNode() == null || dto.getEntryNode().isBlank()) {
            dto.setEntryNode(dto.getNodes().get(0).getCode());
        }

        if (!errors.isEmpty()) {
            throw new MarkdownImportException(errors);
        }
        return dto;
    }

    private boolean parseHeaderMetadataLine(String line, ScenarioSeedDto dto) {
        Matcher m;
        if ((m = CODE_LINE.matcher(line)).matches()) {
            dto.setCode(m.group(1).strip());
            return true;
        }
        if ((m = BLOCK_LINE.matcher(line)).matches()) {
            dto.setBlock(m.group(1).strip());
            return true;
        }
        if ((m = DESCRIPTION_LINE.matcher(line)).matches()) {
            dto.setDescription(m.group(1).strip());
            return true;
        }
        if ((m = FLAGSHIP_LINE.matcher(line)).matches()) {
            dto.setFlagship(isYes(m.group(1)));
            return true;
        }
        if ((m = VERSION_LINE.matcher(line)).matches()) {
            dto.setVersion(Integer.parseInt(m.group(1)));
            return true;
        }
        if ((m = ENTRY_NODE_LINE.matcher(line)).matches()) {
            dto.setEntryNode(m.group(1).strip());
            return true;
        }
        return false;
    }

    private ChoiceSeedDto parseChoiceLine(Matcher choiceM, String line, int lineNo, List<String> errors) {
        ChoiceSeedDto choice = new ChoiceSeedDto();
        choice.setCode(choiceM.group(1).strip());
        choice.setRoleSteps(new RoleStepsSeedDto());
        String body = choiceM.group(2).strip();

        Matcher deltaM = DELTA_TAIL.matcher(body);
        if (!deltaM.matches()) {
            errors.add(lineNo + ": не удалось разобрать эффект на шкалы в варианте '" + choice.getCode()
                    + "' — ожидается формат '... (лояльность +N, безопасность -M)': '" + line + "'");
            choice.setText(body);
            return choice;
        }
        choice.setLoyaltyDelta(Integer.parseInt(deltaM.group(2)));
        choice.setSafetyDelta(Integer.parseInt(deltaM.group(3)));

        String textAndTarget = deltaM.group(1).strip();
        Matcher targetM = TEXT_TARGET.matcher(textAndTarget);
        if (targetM.matches()) {
            choice.setText(targetM.group(1).strip());
            choice.setTarget(targetM.group(2).strip());
        } else {
            choice.setText(textAndTarget);
        }
        return choice;
    }

    private RoleStepsSeedDto parseRoleSteps(String raw, int lineNo, List<String> errors) {
        RoleStepsSeedDto steps = new RoleStepsSeedDto();
        for (String part : raw.split(",")) {
            String word = part.strip().toLowerCase();
            if (word.isEmpty()) {
                continue;
            }
            switch (word) {
                case "признать" -> steps.setAcknowledge(true);
                case "правило" -> steps.setRule(true);
                case "решение" -> steps.setSolution(true);
                case "заверить" -> steps.setReassure(true);
                default -> errors.add(lineNo + ": неизвестный шаг ролевой модели '" + part.strip()
                        + "' (ожидается: признать, правило, решение, заверить)");
            }
        }
        return steps;
    }

    private void finalizeChoice(NodeSeedDto node, ChoiceSeedDto choice) {
        if (node == null || choice == null) {
            return;
        }
        choice.setSortOrder(node.getChoices().size());
        node.getChoices().add(choice);
    }

    private void flushNodeText(NodeSeedDto node, List<String> textLines) {
        if (node == null || node.getText() != null) {
            return;
        }
        node.setText(String.join(" ", textLines).strip());
    }

    private boolean isYes(String word) {
        return word.equalsIgnoreCase("да");
    }

    private boolean isKnownNodeType(String type) {
        try {
            NodeType.valueOf(type);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isKnownOutcome(String outcome) {
        try {
            ScenarioOutcome.valueOf(outcome);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
