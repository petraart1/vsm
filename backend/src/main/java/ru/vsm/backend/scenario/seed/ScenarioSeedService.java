package ru.vsm.backend.scenario.seed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import ru.vsm.backend.scenario.repository.UserProgressRepository;
import ru.vsm.backend.scenario.service.exception.ScenarioHasPlaythroughsException;

/**
 * Транзакционная загрузка одного сценария из {@link ScenarioSeedDto} в БД.
 *
 * <p>Идемпотентность и обновление контента, по {@link ScenarioSeedDto#getVersion()}:
 * <ul>
 *   <li>сценария с таким {@code code} ещё нет — создаётся заново;</li>
 *   <li>уже есть, версия в файле <= версии в БД — файл пропускается целиком, граф не трогается
 *       (обычный путь на каждом рестарте для не изменившегося контента);</li>
 *   <li>уже есть, версия в файле больше версии в БД, и по сценарию ещё нет ни одного
 *       {@code UserProgress} (см. {@link UserProgressRepository#existsByScenarioId}) — старый граф
 *       (узлы+выборы) удаляется и пересобирается заново из файла, сама строка {@code scenarios}
 *       обновляется на месте (id сохраняется);</li>
 *   <li>уже есть, версия в файле больше, но по сценарию есть хотя бы одно прохождение (в т.ч.
 *       {@code COMPLETED}) — обновление пропускается с предупреждением в лог: перезапись узлов/выборов
 *       порвала бы FK из {@code scenario_choice_history} (там нет {@code ON DELETE CASCADE} на
 *       {@code scenario_nodes}/{@code scenario_choices}) и/или {@code user_progress.current_node_id}
 *       у ещё не завершённых прохождений. Обновление такого сценария на непустой БД — ручная операция
 *       (например, на staging/демо-стенде, где прохождения можно потерять осознанно).</li>
 * </ul>
 *
 * <p>Удаление старого графа при обновлении — {@link #deleteExistingGraph}, вручную и в строгом
 * порядке (не просто "удалить узлы и положиться на каскад"): {@code scenario_choices.target_node_id}
 * не имеет {@code ON DELETE CASCADE} и в общем случае указывает вперёд на другие узлы того же
 * сценария, поэтому сначала снимаются все обратные/вперёд смотрящие ссылки, и только потом узлы.
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
    private final UserProgressRepository userProgressRepository;

    @Transactional
    public void seed(ScenarioSeedDto dto) {
        Optional<Scenario> existing = scenarioRepository.findByCode(dto.getCode());
        Scenario reuseScenario = null;
        if (existing.isPresent()) {
            Scenario current = existing.get();
            if (dto.getVersion() <= current.getVersion()) {
                log.info("Сценарий '{}' версии {} уже есть в БД (файл принёс версию {}) — пропускаю.",
                        dto.getCode(), current.getVersion(), dto.getVersion());
                return;
            }
            if (userProgressRepository.existsByScenarioId(current.getId())) {
                log.warn("Сценарий '{}': файл принёс версию {} (в БД {}), но по сценарию уже есть "
                                + "прохождения — обновление графа пропущено, чтобы не порвать FK "
                                + "истории/прогресса. Нужна ручная миграция контента на этой БД.",
                        dto.getCode(), dto.getVersion(), current.getVersion());
                return;
            }
            log.info("Сценарий '{}': обновляю граф с версии {} до {} (прохождений ещё не было).",
                    dto.getCode(), current.getVersion(), dto.getVersion());
            deleteExistingGraph(current);
            reuseScenario = current;
        }
        if (dto.getNodes() == null || dto.getNodes().isEmpty()) {
            throw new IllegalStateException("Сценарий '" + dto.getCode() + "': нет узлов (nodes) в seed-файле");
        }

        Scenario scenario;
        if (reuseScenario != null) {
            reuseScenario.setSituationRefId(dto.getSituationRef());
            reuseScenario.setBlock(dto.getBlock());
            reuseScenario.setTitle(dto.getTitle());
            reuseScenario.setDescription(dto.getDescription());
            reuseScenario.setFlagship(dto.isFlagship());
            reuseScenario.setVersion(dto.getVersion());
            scenario = scenarioRepository.save(reuseScenario);
        } else {
            scenario = scenarioRepository.save(Scenario.builder()
                    .code(dto.getCode())
                    .situationRefId(dto.getSituationRef())
                    .block(dto.getBlock())
                    .title(dto.getTitle())
                    .description(dto.getDescription())
                    .flagship(dto.isFlagship())
                    .version(dto.getVersion())
                    .build());
        }
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
                        .explanation(c.getExplanation())
                        .normRef(c.getNormRef())
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

    /**
     * Вариант {@link #seed(ScenarioSeedDto)} для редактора сценариев (в отличие от загрузки при
     * старте приложения): вызывающий уже проверил граф {@code ScenarioGraphValidator}'ом, здесь
     * только правила версионирования содержимого.
     *
     * <ul>
     *   <li>сценария с таким {@code code} ещё нет — создаётся (как обычный {@link #seed});</li>
     *   <li>уже есть, но по нему есть хотя бы одно прохождение — {@link ScenarioHasPlaythroughsException}
     *       (409 на HTTP-уровне), граф не трогается;</li>
     *   <li>уже есть и прохождений ещё не было — обновляется ВСЕГДА (в отличие от {@link #seed},
     *       который тихо пропускает файл при {@code version <= текущая}): версия из {@code dto}
     *       принудительно поднимается до {@code текущая + 1}, если автор редактора не поднял её
     *       сам, — иначе правка в редакторе с той же версией молча проигнорировалась бы.</li>
     * </ul>
     *
     * @return id сохранённого сценария (нового или обновлённого)
     */
    @Transactional
    public UUID upsertForEditor(ScenarioSeedDto dto) {
        Optional<Scenario> existing = scenarioRepository.findByCode(dto.getCode());
        if (existing.isPresent()) {
            Scenario current = existing.get();
            if (userProgressRepository.existsByScenarioId(current.getId())) {
                throw new ScenarioHasPlaythroughsException("Сценарий '" + dto.getCode()
                        + "' уже проходили — обновление графа недоступно, чтобы не сломать историю"
                        + " прохождений. Создайте новый сценарий с другим code.");
            }
            if (dto.getVersion() <= current.getVersion()) {
                dto.setVersion(current.getVersion() + 1);
            }
        }
        seed(dto);
        return scenarioRepository.findByCode(dto.getCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Сценарий '" + dto.getCode() + "' не найден сразу после сохранения — не должно происходить"))
                .getId();
    }

    /**
     * Удаляет весь существующий граф сценария (узлы+выборы) перед перезаписью более новой версией.
     * Порядок принципиален из-за трёх FK без {@code ON DELETE CASCADE} в обратную сторону
     * ({@code scenarios.entry_node_id}, {@code scenario_nodes.default_choice_id},
     * {@code scenario_choices.target_node_id}) — граф в общем случае содержит и "вперёд смотрящие",
     * и обратные ссылки между узлами (см. javadoc класса про циклы), поэтому нельзя просто удалить
     * узлы: любой ещё не удалённый выбор, у которого {@code target_node_id} указывает на уже
     * удаляемый узел, оборвёт constraint. Разрываем ссылки в правильном порядке вместо того чтобы
     * полагаться на порядок каскадов:
     * <ol>
     *   <li>{@code scenarios.entry_node_id} → null (уже сохранённый сценарий не должен указывать
     *       на узел, который сейчас будет удалён);</li>
     *   <li>{@code scenario_nodes.default_choice_id} → null на всех узлах сценария (иначе следующий
     *       шаг не сможет удалить их собственные выборы по умолчанию);</li>
     *   <li>удалить все {@code scenario_choices} этих узлов (это же снимает все
     *       {@code target_node_id}-ссылки на другие узлы того же сценария, т.к. ссылающиеся строки
     *       исчезают целиком);</li>
     *   <li>удалить сами {@code scenario_nodes} — на них уже никто не ссылается.</li>
     * </ol>
     */
    private void deleteExistingGraph(Scenario scenario) {
        scenario.setEntryNodeId(null);
        scenarioRepository.saveAndFlush(scenario);

        List<ScenarioNode> oldNodes = scenarioNodeRepository.findByScenarioId(scenario.getId());
        for (ScenarioNode n : oldNodes) {
            if (n.getDefaultChoiceId() != null) {
                n.setDefaultChoiceId(null);
            }
        }
        scenarioNodeRepository.saveAllAndFlush(oldNodes);

        for (ScenarioNode n : oldNodes) {
            List<ScenarioChoice> choices = scenarioChoiceRepository.findByNodeIdOrderBySortOrder(n.getId());
            if (!choices.isEmpty()) {
                scenarioChoiceRepository.deleteAll(choices);
            }
        }
        scenarioChoiceRepository.flush();

        scenarioNodeRepository.deleteAll(oldNodes);
        scenarioNodeRepository.flush();
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
