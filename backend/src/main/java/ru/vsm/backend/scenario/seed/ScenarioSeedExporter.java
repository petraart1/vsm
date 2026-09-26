package ru.vsm.backend.scenario.seed;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.domain.ScenarioChoice;
import ru.vsm.backend.scenario.domain.ScenarioNode;
import ru.vsm.backend.scenario.domain.ScenarioNodePortrait;
import ru.vsm.backend.scenario.repository.ScenarioChoiceRepository;
import ru.vsm.backend.scenario.repository.ScenarioNodePortraitRepository;
import ru.vsm.backend.scenario.repository.ScenarioNodeRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.service.exception.ScenarioNotFoundException;

/**
 * Обратное преобразование персистентного графа сценария (id-ссылки) в {@link ScenarioSeedDto}
 * (код-ссылки) — тот же JSON-формат, что и файлы {@code classpath:scenarios/*.json}.
 *
 * <p>Используется редактором сценариев (экспорт существующего сценария для правки/round-trip)
 * и {@code ScenarioGraphValidationIntegrationTest} — единая точка "БД -> seed-формат", чтобы
 * граф из БД можно было прогнать через тот же {@code ScenarioGraphValidator}, что и граф из
 * входящего JSON редактора, без дублирования логики маппинга.
 */
@Component
@RequiredArgsConstructor
public class ScenarioSeedExporter {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioNodeRepository scenarioNodeRepository;
    private final ScenarioChoiceRepository scenarioChoiceRepository;
    private final ScenarioNodePortraitRepository scenarioNodePortraitRepository;

    public ScenarioSeedDto export(String code) {
        Scenario scenario = scenarioRepository.findByCode(code)
                .orElseThrow(() -> new ScenarioNotFoundException("Сценарий '" + code + "' не найден"));
        return export(scenario);
    }

    public ScenarioSeedDto export(Scenario scenario) {
        List<ScenarioNode> nodes = scenarioNodeRepository.findByScenarioId(scenario.getId());
        Map<UUID, String> nodeCodeById = new HashMap<>();
        for (ScenarioNode n : nodes) {
            nodeCodeById.put(n.getId(), n.getCode());
        }

        Map<UUID, List<ScenarioChoice>> choicesByNodeId = new HashMap<>();
        Map<UUID, String> choiceCodeById = new HashMap<>();
        for (ScenarioNode n : nodes) {
            List<ScenarioChoice> choices = scenarioChoiceRepository.findByNodeIdOrderBySortOrder(n.getId());
            choicesByNodeId.put(n.getId(), choices);
            for (ScenarioChoice c : choices) {
                choiceCodeById.put(c.getId(), c.getCode());
            }
        }

        ScenarioSeedDto dto = new ScenarioSeedDto();
        dto.setCode(scenario.getCode());
        dto.setSituationRef(scenario.getSituationRefId());
        dto.setBlock(scenario.getBlock());
        dto.setTitle(scenario.getTitle());
        dto.setDescription(scenario.getDescription());
        dto.setFlagship(scenario.isFlagship());
        dto.setVersion(scenario.getVersion());
        dto.setEntryNode(scenario.getEntryNodeId() != null ? nodeCodeById.get(scenario.getEntryNodeId()) : null);

        for (ScenarioNode n : nodes) {
            NodeSeedDto nodeDto = new NodeSeedDto();
            nodeDto.setCode(n.getCode());
            nodeDto.setType(n.getNodeType() != null ? n.getNodeType().name() : null);
            nodeDto.setText(n.getText());
            nodeDto.setTimerSeconds(n.getTimerSeconds());
            nodeDto.setDefaultChoice(n.getDefaultChoiceId() != null ? choiceCodeById.get(n.getDefaultChoiceId()) : null);
            nodeDto.setTerminal(n.isTerminal());
            nodeDto.setTerminalOutcome(n.getTerminalOutcome() != null ? n.getTerminalOutcome().name() : null);
            nodeDto.setOutcomeSummary(n.getOutcomeSummary());
            nodeDto.setHiddenFromPassenger(n.isHiddenFromPassenger());
            Map<String, String> portraits = new LinkedHashMap<>();
            for (ScenarioNodePortrait p : scenarioNodePortraitRepository.findByNodeId(n.getId())) {
                portraits.put(p.getCarClass().name(), p.getText());
            }
            nodeDto.setPassengerPortraits(portraits);

            for (ScenarioChoice c : choicesByNodeId.getOrDefault(n.getId(), List.of())) {
                ChoiceSeedDto choiceDto = new ChoiceSeedDto();
                choiceDto.setCode(c.getCode());
                choiceDto.setText(c.getText());
                choiceDto.setLoyaltyDelta(c.getLoyaltyDelta());
                choiceDto.setSafetyDelta(c.getSafetyDelta());
                choiceDto.setTarget(c.getTargetNodeId() != null ? nodeCodeById.get(c.getTargetNodeId()) : null);
                RoleStepsSeedDto roleSteps = new RoleStepsSeedDto();
                if (c.getRoleSteps() != null) {
                    roleSteps.setAcknowledge(c.getRoleSteps().isAcknowledge());
                    roleSteps.setRule(c.getRoleSteps().isRule());
                    roleSteps.setSolution(c.getRoleSteps().isSolution());
                    roleSteps.setReassure(c.getRoleSteps().isReassure());
                }
                choiceDto.setRoleSteps(roleSteps);
                choiceDto.setExplanationKey(c.getExplanationKey());
                choiceDto.setExplanation(c.getExplanation());
                choiceDto.setNormRef(c.getNormRef());
                choiceDto.setSortOrder(c.getSortOrder());
                nodeDto.getChoices().add(choiceDto);
            }
            dto.getNodes().add(nodeDto);
        }
        return dto;
    }
}
