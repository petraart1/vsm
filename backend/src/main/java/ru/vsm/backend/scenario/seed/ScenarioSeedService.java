package ru.vsm.backend.scenario.seed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.scenario.domain.NodeType;
import ru.vsm.backend.scenario.domain.RoleStepFlags;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.domain.ScenarioChoice;
import ru.vsm.backend.scenario.domain.ScenarioNode;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.repository.ScenarioChoiceRepository;
import ru.vsm.backend.scenario.repository.ScenarioNodeRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;

/**
 * Транзакционная загрузка одного сценария из {@link ScenarioSeedDto} в БД.
 *
 * <p>Идемпотентность: если сценарий с таким {@code code} уже существует — файл пропускается
 * целиком (никаких апдейтов существующего графа). Так повторные перезапуски приложения не плодят
 * дубликаты и не рвут уже начатые {@code UserProgress} у игроков (FK на узлы/выборы остаются валидными).
 *
 * <p>Вынесено в отдельный бин (а не метод в {@link ScenarioSeedLoader}), чтобы
 * {@code @Transactional} применялся через Spring-прокси, а не терялся на self-invocation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioSeedService {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioNodeRepository scenarioNodeRepository;
    private final ScenarioChoiceRepository scenarioChoiceRepository;

    @Transactional
    public void seed(ScenarioSeedDto dto) {
        if (scenarioRepository.existsByCode(dto.getCode())) {
            log.info("Сценарий '{}' уже есть в БД — пропускаю (идемпотентный seed).", dto.getCode());
            return;
        }
        if (dto.getNodes() == null || dto.getNodes().isEmpty()) {
            throw new IllegalStateException("Сценарий '" + dto.getCode() + "': нет узлов (nodes) в seed-файле");
        }

        Scenario scenario = Scenario.builder()
                .code(dto.getCode())
                .situationRefId(dto.getSituationRef())
                .block(dto.getBlock())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .flagship(dto.isFlagship())
                .build();
        scenario = scenarioRepository.save(scenario);
        UUID scenarioId = scenario.getId();

        // Проход 1: создать все узлы без default_choice_id (choices ещё не существуют).
        Map<String, ScenarioNode> nodesByCode = new HashMap<>();
        for (NodeSeedDto n : dto.getNodes()) {
            ScenarioNode node = ScenarioNode.builder()
                    .scenarioId(scenarioId)
                    .code(n.getCode())
                    .nodeType(parseNodeType(dto.getCode(), n))
                    .text(n.getText())
                    .timerSeconds(n.getTimerSeconds())
                    .terminal(n.isTerminal())
                    .terminalOutcome(parseOutcome(dto.getCode(), n.getCode(), n.getTerminalOutcome()))
                    .outcomeSummary(n.getOutcomeSummary())
                    .build();
            nodesByCode.put(n.getCode(), scenarioNodeRepository.save(node));
        }

        ScenarioNode entryNode = nodesByCode.get(dto.getEntryNode());
        if (entryNode == null) {
            throw new IllegalStateException(
                    "Сценарий '" + dto.getCode() + "': entryNode '" + dto.getEntryNode() + "' не найден среди узлов");
        }

        // Проход 2: создать все выборы, резолвя target по коду узла (в т.ч. вперёд/назад/на себя).
        Map<String, ScenarioChoice> choicesByNodeAndCode = new HashMap<>();
        for (NodeSeedDto n : dto.getNodes()) {
            ScenarioNode node = nodesByCode.get(n.getCode());
            List<ChoiceSeedDto> choices = n.getChoices();
            if (node.isTerminal() && (choices == null || choices.isEmpty())) {
                continue;
            }
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException(
                        "Сценарий '" + dto.getCode() + "', узел '" + n.getCode()
                                + "': нет ни одного выбора, а узел не терминальный");
            }
            for (ChoiceSeedDto c : choices) {
                UUID targetNodeId = null;
                if (c.getTarget() != null) {
                    ScenarioNode target = nodesByCode.get(c.getTarget());
                    if (target == null) {
                        throw new IllegalStateException("Сценарий '" + dto.getCode() + "', выбор '" + n.getCode()
                                + "." + c.getCode() + "': target '" + c.getTarget() + "' не найден среди узлов");
                    }
                    targetNodeId = target.getId();
                }
                RoleStepsSeedDto rs = c.getRoleSteps() != null ? c.getRoleSteps() : new RoleStepsSeedDto();
                ScenarioChoice choice = ScenarioChoice.builder()
                        .nodeId(node.getId())
                        .code(c.getCode())
                        .text(c.getText())
                        .loyaltyDelta(c.getLoyaltyDelta())
                        .safetyDelta(c.getSafetyDelta())
                        .targetNodeId(targetNodeId)
                        .roleSteps(RoleStepFlags.builder()
                                .acknowledge(rs.isAcknowledge())
                                .rule(rs.isRule())
                                .solution(rs.isSolution())
                                .reassure(rs.isReassure())
                                .build())
                        .explanationKey(c.getExplanationKey())
                        .sortOrder(c.getSortOrder())
                        .build();
                choicesByNodeAndCode.put(n.getCode() + "::" + c.getCode(), scenarioChoiceRepository.save(choice));
            }
        }

        // Проход 3: проставить default_choice_id на узлах и entry_node_id на сценарии.
        for (NodeSeedDto n : dto.getNodes()) {
            if (n.getDefaultChoice() == null) {
                continue;
            }
            ScenarioChoice defaultChoice = choicesByNodeAndCode.get(n.getCode() + "::" + n.getDefaultChoice());
            if (defaultChoice == null) {
                throw new IllegalStateException("Сценарий '" + dto.getCode() + "', узел '" + n.getCode()
                        + "': defaultChoice '" + n.getDefaultChoice() + "' не найден среди его выборов");
            }
            ScenarioNode node = nodesByCode.get(n.getCode());
            node.setDefaultChoiceId(defaultChoice.getId());
            scenarioNodeRepository.save(node);
        }

        scenario.setEntryNodeId(entryNode.getId());
        scenarioRepository.save(scenario);

        log.info("Сценарий '{}' загружен: {} узлов, {} выборов.",
                dto.getCode(), nodesByCode.size(), choicesByNodeAndCode.size());
    }

    private NodeType parseNodeType(String scenarioCode, NodeSeedDto n) {
        try {
            return NodeType.valueOf(n.getType());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("Сценарий '" + scenarioCode + "', узел '" + n.getCode()
                    + "': некорректный type '" + n.getType() + "'", e);
        }
    }

    private ScenarioOutcome parseOutcome(String scenarioCode, String nodeCode, String outcome) {
        if (outcome == null) {
            return null;
        }
        try {
            return ScenarioOutcome.valueOf(outcome);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Сценарий '" + scenarioCode + "', узел '" + nodeCode
                    + "': некорректный terminalOutcome '" + outcome + "'", e);
        }
    }
}
