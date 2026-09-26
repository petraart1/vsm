package ru.vsm.backend.scenario.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.domain.NodeType;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.seed.ChoiceSeedDto;
import ru.vsm.backend.scenario.seed.NodeSeedDto;
import ru.vsm.backend.scenario.seed.ScenarioSeedDto;

/**
 * Проверяет целостность графа сценария в том же формате, в котором граф приходит в seed-файле
 * ({@link ScenarioSeedDto}) — до сохранения в БД. Возвращает список человекочитаемых проблем
 * вместо исключений, чтобы вызывающий код (редактор сценариев, тесты) сам решал, что делать
 * с результатом: показать список ошибок клиенту (400) или упасть в тесте.
 *
 * <p>Объединяет две группы инвариантов:
 * <ul>
 *   <li>"локальные" — те же, что уже проверяет {@code ScenarioSeedService} при загрузке
 *       (существование entryNode/target/defaultChoice, обязательность choices у нетерминальных
 *       узлов, корректность enum'ов) — но здесь без исключений и до похода в БД;</li>
 *   <li>"глобальные" — достижимость каждого узла из {@code entryNode} и наличие хотя бы одного
 *       достижимого терминального узла (не проверяются сидером на вставке одной строки, но ломают
 *       прохождение в рантайме).</li>
 * </ul>
 *
 * <p>Используется редактором сценариев (валидация перед сохранением) и
 * {@code ScenarioGraphValidationIntegrationTest} (тот же валидатор прогоняется на графах,
 * экспортированных из БД через {@code ScenarioSeedExporter} — единая логика проверки и для
 * "сценария из файла", и для "сценария, уже осевшего в БД").
 */
@Component
public class ScenarioGraphValidator {

    public List<String> validate(ScenarioSeedDto dto) {
        List<String> errors = new ArrayList<>();
        if (dto == null) {
            errors.add("тело запроса пустое");
            return errors;
        }
        String label = dto.getCode() == null || dto.getCode().isBlank() ? "<без кода>" : dto.getCode();
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            errors.add("code сценария обязателен");
        }
        if (dto.getBlock() == null || dto.getBlock().isBlank()) {
            errors.add("сценарий '" + label + "': block обязателен");
        }
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            errors.add("сценарий '" + label + "': title обязателен");
        }
        List<NodeSeedDto> nodes = dto.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            errors.add("сценарий '" + label + "': нет ни одного узла (nodes)");
            return errors;
        }

        Map<String, NodeSeedDto> nodesByCode = new HashMap<>();
        for (NodeSeedDto n : nodes) {
            if (n.getCode() == null || n.getCode().isBlank()) {
                errors.add("сценарий '" + label + "': встречен узел без code");
                continue;
            }
            if (nodesByCode.put(n.getCode(), n) != null) {
                errors.add("сценарий '" + label + "': код узла '" + n.getCode() + "' повторяется");
            }
        }

        if (dto.getEntryNode() == null || dto.getEntryNode().isBlank()) {
            errors.add("сценарий '" + label + "': entryNode не указан");
        } else if (!nodesByCode.containsKey(dto.getEntryNode())) {
            errors.add("сценарий '" + label + "': entryNode '" + dto.getEntryNode() + "' не найден среди узлов");
        }

        // Локальные инварианты каждого узла/выбора.
        Map<String, Set<String>> choiceCodesByNode = new HashMap<>();
        for (NodeSeedDto n : nodes) {
            if (n.getCode() == null || n.getCode().isBlank()) {
                continue;
            }
            validateNodeType(label, n, errors);
            validatePassengerPortraits(n, errors);
            List<ChoiceSeedDto> choices = n.getChoices();
            boolean hasChoices = choices != null && !choices.isEmpty();

            if (n.isTerminal()) {
                if (hasChoices) {
                    errors.add("узел '" + n.getCode() + "': терминальный узел не должен иметь выборов");
                }
                validateTerminalOutcome(label, n, errors);
            } else if (!hasChoices) {
                errors.add("узел '" + n.getCode() + "': нетерминальный узел должен иметь хотя бы один выбор");
            }

            Set<String> choiceCodes = new HashSet<>();
            if (hasChoices) {
                for (ChoiceSeedDto c : choices) {
                    if (c.getCode() == null || c.getCode().isBlank()) {
                        errors.add("узел '" + n.getCode() + "': встречен выбор без code");
                        continue;
                    }
                    if (!choiceCodes.add(c.getCode())) {
                        errors.add("узел '" + n.getCode() + "': код выбора '" + c.getCode() + "' повторяется");
                    }
                    if (c.getTarget() != null && !nodesByCode.containsKey(c.getTarget())) {
                        errors.add("выбор '" + n.getCode() + "." + c.getCode() + "': target '" + c.getTarget()
                                + "' не найден среди узлов сценария");
                    }
                }
            }
            choiceCodesByNode.put(n.getCode(), choiceCodes);

            if (n.getTimerSeconds() != null) {
                if (n.getDefaultChoice() == null || n.getDefaultChoice().isBlank()) {
                    errors.add("узел '" + n.getCode() + "': задан timerSeconds, но нет defaultChoice");
                } else if (!choiceCodes.contains(n.getDefaultChoice())) {
                    errors.add("узел '" + n.getCode() + "': defaultChoice '" + n.getDefaultChoice()
                            + "' не найден среди выборов этого же узла");
                }
            }
        }

        if (!errors.isEmpty() || dto.getEntryNode() == null || !nodesByCode.containsKey(dto.getEntryNode())) {
            // Достижимость проверяем только на структурно валидном графе — иначе BFS по битым
            // ссылкам добавит производные, малополезные ошибки поверх уже найденных.
            return errors;
        }

        validateReachability(label, dto.getEntryNode(), nodesByCode, errors);
        return errors;
    }

    private void validateNodeType(String label, NodeSeedDto n, List<String> errors) {
        try {
            NodeType.valueOf(n.getType());
        } catch (IllegalArgumentException | NullPointerException e) {
            errors.add("узел '" + n.getCode() + "': некорректный type '" + n.getType()
                    + "' (ожидается DIALOGUE/ESCALATION/TERMINAL)");
        }
    }

    /**
     * «Портрет пассажира»: ключи {@code passengerPortraits} — коды {@link CarClass}, не
     * произвольные строки.
     */
    private void validatePassengerPortraits(NodeSeedDto n, List<String> errors) {
        if (n.getPassengerPortraits() == null) {
            return;
        }
        for (String carClassCode : n.getPassengerPortraits().keySet()) {
            try {
                CarClass.valueOf(carClassCode);
            } catch (IllegalArgumentException e) {
                errors.add("узел '" + n.getCode() + "': некорректный класс вагона в passengerPortraits '"
                        + carClassCode + "' (ожидается STANDARD/COMFORT/BUSINESS/FIRST)");
            }
        }
    }

    private void validateTerminalOutcome(String label, NodeSeedDto n, List<String> errors) {
        if (n.getTerminalOutcome() == null) {
            errors.add("узел '" + n.getCode() + "': terminal=true, но terminalOutcome не указан");
            return;
        }
        try {
            ScenarioOutcome.valueOf(n.getTerminalOutcome());
        } catch (IllegalArgumentException e) {
            errors.add("узел '" + n.getCode() + "': некорректный terminalOutcome '" + n.getTerminalOutcome()
                    + "' (ожидается SUCCESS/PARTIAL/FAILURE)");
        }
    }

    private void validateReachability(
            String label, String entryNode, Map<String, NodeSeedDto> nodesByCode, List<String> errors) {
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(entryNode);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!reachable.add(current)) {
                continue;
            }
            NodeSeedDto node = nodesByCode.get(current);
            if (node == null || node.getChoices() == null) {
                continue;
            }
            for (ChoiceSeedDto c : node.getChoices()) {
                if (c.getTarget() != null) {
                    queue.add(c.getTarget());
                }
            }
        }

        Set<String> unreachable = new HashSet<>();
        for (String code : nodesByCode.keySet()) {
            if (!reachable.contains(code)) {
                unreachable.add(code);
            }
        }
        if (!unreachable.isEmpty()) {
            errors.add("сценарий '" + label + "': узлы недостижимы из entryNode: " + unreachable);
        }

        boolean hasReachableTerminal = reachable.stream()
                .map(nodesByCode::get)
                .filter(java.util.Objects::nonNull)
                .anyMatch(NodeSeedDto::isTerminal);
        if (!hasReachableTerminal) {
            errors.add("сценарий '" + label + "': нет ни одного достижимого терминального узла");
        }
    }
}
