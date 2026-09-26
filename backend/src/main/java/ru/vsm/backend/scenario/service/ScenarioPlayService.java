package ru.vsm.backend.scenario.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.RoleStepFlags;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.domain.ScenarioChoice;
import ru.vsm.backend.scenario.domain.ScenarioChoiceHistory;
import ru.vsm.backend.scenario.domain.ScenarioNode;
import ru.vsm.backend.scenario.domain.ScenarioNodePortrait;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.domain.UserProgress;
import ru.vsm.backend.scenario.event.ProgressStateChangedEvent;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;
import ru.vsm.backend.scenario.repository.ScenarioChoiceHistoryRepository;
import ru.vsm.backend.scenario.repository.ScenarioChoiceRepository;
import ru.vsm.backend.scenario.repository.ScenarioNodePortraitRepository;
import ru.vsm.backend.scenario.repository.ScenarioNodeRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.repository.UserProgressRepository;
import ru.vsm.backend.scenario.service.exception.ChoiceNotAvailableException;
import ru.vsm.backend.scenario.service.exception.NoActiveTimerException;
import ru.vsm.backend.scenario.service.exception.ProgressAccessDeniedException;
import ru.vsm.backend.scenario.service.exception.ProgressAlreadyCompletedException;
import ru.vsm.backend.scenario.service.exception.ProgressNotFoundException;
import ru.vsm.backend.scenario.service.exception.ScenarioNotFoundException;
import ru.vsm.backend.scenario.web.dto.ChoiceAppliedResponse;
import ru.vsm.backend.scenario.web.dto.ChoiceOptionResponse;
import ru.vsm.backend.scenario.web.dto.NodeStateResponse;
import ru.vsm.backend.scenario.web.dto.ProgressStateResponse;

/**
 * Прохождение сценария игроком: старт, чтение текущего узла, применение выбора, явный таймаут.
 *
 * <p><b>Серверный таймер</b> — источник истины: {@link UserProgress#getNodeDeadlineAt()}
 * проставляется при входе в узел с {@code timerSeconds} (now + timerSeconds) и сверяется при
 * каждом выборе. Если выбор пришёл после дедлайна — он игнорируется и применяется
 * {@code defaultChoice} узла (с пометкой {@code wasTimeout=true} в истории), а не то, что прислал
 * клиент. WebSocket-пуш обратного отсчёта (следующая задача) только визуализирует этот дедлайн,
 * не заменяет проверку здесь.
 *
 * <p><b>Раскрытие дельт шкал</b>: {@link ChoiceOptionResponse} (до выбора) не содержит
 * loyalty/safety дельт — они появляются только в {@link ChoiceAppliedResponse} после того, как
 * выбор уже сделан и применён.
 *
 * <p><b>«Портрет пассажира»</b>: {@link UserProgress#getCarClass()} фиксируется один раз при
 * старте прохождения ({@link #start}) и модифицирует дельту лояльности каждого применённого
 * выбора (см. {@link CarClass#modifyLoyaltyDelta}, применяется до {@link #clampScale}), а также
 * подменяет текст узла на переопределение для этого класса, если оно задано (см.
 * {@link #toNodeState}). Рейтинг безопасности от класса вагона не зависит.
 *
 * <p><b>Завершение прохождения</b> происходит в двух случаях: (1) выбор ведёт в узел с
 * {@code terminal=true} — тогда исход берётся из {@code terminalOutcome} этого узла; (2) у самого
 * выбора {@code target == null} (конец сразу после выбора, без отдельного терминального узла) —
 * такой путь пока не используется во флагманских сценариях, но схема (см. Javadoc
 * {@link ScenarioChoice#getTargetNodeId()}) его допускает, поэтому обрабатывается здесь с
 * fallback-исходом {@link ScenarioOutcome#PARTIAL} (нет текста/резюме — их взять неоткуда).
 * В обоих случаях {@link ScenarioCompletedEvent} публикуется через
 * {@link ApplicationEventPublisher#publishEvent(Object)} внутри этого же {@code @Transactional}
 * метода (до коммита) — слушатели домена (gamification/feedback) используют
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, чтобы не видеть событие, если
 * транзакция потом откатится.
 *
 * <p>Каждое применение выбора (в {@link #applyResolvedChoice}) также публикует
 * {@link ru.vsm.backend.scenario.event.ProgressStateChangedEvent} — точка подписки для
 * WebSocket-пакета {@code ru.vsm.backend.ws} (живые обновления шкал/узла/статуса для
 * подключённых клиентов), не только для {@link ScenarioCompletedEvent} на завершении.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioPlayService {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioNodeRepository scenarioNodeRepository;
    private final ScenarioChoiceRepository scenarioChoiceRepository;
    private final UserProgressRepository userProgressRepository;
    private final ScenarioChoiceHistoryRepository scenarioChoiceHistoryRepository;
    private final ScenarioNodePortraitRepository scenarioNodePortraitRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Начинает новое прохождение сценария игроком, либо возвращает уже начатое (IN_PROGRESS) —
     * повторный вызов start для того же (playerId, scenarioId) не плодит дубликаты прогресса.
     *
     * <p>{@code carClass} — класс вагона ("портрет пассажира", см. {@link CarClass}), в котором
     * игрок проходит сценарий; фиксируется только при создании нового прохождения и модифицирует
     * дельту лояльности каждого применённого выбора до конца этого прохождения. Если уже есть
     * незавершённое прохождение того же (playerId, scenarioId) — его класс не меняется, даже
     * если в этом вызове передан другой (менять класс на середине прохождения означало бы
     * задним числом переинтерпретировать уже применённые дельты).
     */
    @Transactional
    public ProgressStateResponse start(UUID scenarioId, UUID playerId, CarClass carClass) {
        return start(scenarioId, playerId, carClass, null);
    }

    /**
     * Как {@link #start(UUID, UUID, CarClass)}, но помечает создаваемое прохождение как пункт
     * экзамена ({@code examId != null}) — используется только {@code ExamService.startCurrentScenario}.
     * Прохождение с {@code examMode=true} ведёт себя как обычное во всём, кроме одного отличия: в
     * {@link ChoiceAppliedResponse} каждого применённого выбора не раскрываются дельты/итоговые
     * значения шкал (см. Javadoc {@link ChoiceAppliedResponse}).
     */
    @Transactional
    public ProgressStateResponse start(UUID scenarioId, UUID playerId, CarClass carClass, UUID examId) {
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .filter(Scenario::isActive)
                .orElseThrow(() -> new ScenarioNotFoundException("Сценарий '" + scenarioId + "' не найден"));

        UserProgress progress = userProgressRepository
                .findByUserIdAndScenarioIdAndStatus(playerId, scenarioId, ProgressStatus.IN_PROGRESS)
                .orElseGet(() -> createProgress(scenario, playerId, carClass, examId));

        ScenarioNode currentNode = requireNode(progress.getCurrentNodeId());
        return toProgressState(progress, scenario, currentNode);
    }

    @Transactional(readOnly = true)
    public ProgressStateResponse getProgress(UUID progressId, UUID playerId) {
        UserProgress progress = requireProgress(progressId);
        checkOwnership(progress, playerId);
        Scenario scenario = requireScenario(progress.getScenarioId());
        ScenarioNode currentNode = progress.getCurrentNodeId() != null ? requireNode(progress.getCurrentNodeId()) : null;
        return toProgressState(progress, scenario, currentNode);
    }

    /**
     * Применяет выбор игрока. Если дедлайн текущего узла уже прошёл, запрошенный
     * {@code requestedChoiceId} игнорируется и вместо него применяется default-выбор узла.
     */
    @Transactional
    public ChoiceAppliedResponse choose(UUID progressId, UUID playerId, UUID requestedChoiceId) {
        UserProgress progress = requireProgressForUpdate(progressId);
        checkOwnership(progress, playerId);
        requireInProgress(progress);
        ScenarioNode currentNode = requireNode(progress.getCurrentNodeId());

        UUID effectiveChoiceId = requestedChoiceId;
        boolean wasTimeout = false;
        if (isPastDeadline(progress)) {
            if (currentNode.getDefaultChoiceId() != null) {
                effectiveChoiceId = currentNode.getDefaultChoiceId();
                wasTimeout = true;
            } else {
                log.warn("Узел '{}' сценария {} просрочен, но default_choice не задан — применяю запрошенный выбор",
                        currentNode.getCode(), progress.getScenarioId());
            }
        }

        ScenarioChoice choice = findChoiceOnNode(currentNode.getId(), effectiveChoiceId);
        return applyResolvedChoice(progress, currentNode, choice, wasTimeout);
    }

    /** Явный запрос клиента "время вышло" — форсирует default-выбор узла независимо от дедлайна. */
    @Transactional
    public ChoiceAppliedResponse timeout(UUID progressId, UUID playerId) {
        UserProgress progress = requireProgressForUpdate(progressId);
        checkOwnership(progress, playerId);
        requireInProgress(progress);
        ScenarioNode currentNode = requireNode(progress.getCurrentNodeId());
        if (currentNode.getDefaultChoiceId() == null) {
            throw new NoActiveTimerException(
                    "У узла '" + currentNode.getCode() + "' нет таймера/выбора по умолчанию");
        }
        ScenarioChoice defaultChoice = findChoiceOnNode(currentNode.getId(), currentNode.getDefaultChoiceId());
        return applyResolvedChoice(progress, currentNode, defaultChoice, true);
    }

    private ChoiceAppliedResponse applyResolvedChoice(
            UserProgress progress, ScenarioNode fromNode, ScenarioChoice choice, boolean wasTimeout) {
        Instant now = Instant.now();

        int loyaltyBefore = progress.getLoyaltyScore();
        int safetyBefore = progress.getSafetyScore();
        // «Портрет пассажира»: модификатор класса вагона меняет только лояльность (см. javadoc
        // CarClass) — рейтинг безопасности объективен и класса вагона не касается.
        int modifiedLoyaltyDelta = progress.getCarClass().modifyLoyaltyDelta(choice.getLoyaltyDelta());
        int loyaltyAfter = clampScale(loyaltyBefore + modifiedLoyaltyDelta);
        int safetyAfter = clampScale(safetyBefore + choice.getSafetyDelta());
        int appliedLoyaltyDelta = loyaltyAfter - loyaltyBefore;
        int appliedSafetyDelta = safetyAfter - safetyBefore;

        long sequenceIndex = scenarioChoiceHistoryRepository.countByUserProgressId(progress.getId());
        scenarioChoiceHistoryRepository.save(ScenarioChoiceHistory.builder()
                .userProgressId(progress.getId())
                .nodeId(fromNode.getId())
                .choiceId(choice.getId())
                .sequenceIndex((int) sequenceIndex)
                .wasTimeout(wasTimeout)
                .loyaltyDeltaApplied(appliedLoyaltyDelta)
                .safetyDeltaApplied(appliedSafetyDelta)
                .chosenAt(now)
                .build());

        progress.setLoyaltyScore(loyaltyAfter);
        progress.setSafetyScore(safetyAfter);
        progress.setUpdatedAt(now);

        ScenarioNode nextNode = choice.getTargetNodeId() != null ? requireNode(choice.getTargetNodeId()) : null;
        NodeStateResponse nextNodeResponse;

        if (nextNode != null && nextNode.isTerminal()) {
            ScenarioOutcome outcome = nextNode.getTerminalOutcome() != null
                    ? nextNode.getTerminalOutcome()
                    : ScenarioOutcome.PARTIAL;
            completeProgress(progress, outcome, now);
            nextNodeResponse = toNodeState(nextNode, List.of(), null, progress.getCarClass());
        } else if (nextNode != null) {
            progress.setCurrentNodeId(nextNode.getId());
            progress.setNodeDeadlineAt(
                    nextNode.getTimerSeconds() != null ? now.plusSeconds(nextNode.getTimerSeconds()) : null);
            nextNodeResponse = toNodeState(
                    nextNode, scenarioChoiceRepository.findByNodeIdOrderBySortOrder(nextNode.getId()),
                    progress.getNodeDeadlineAt(), progress.getCarClass());
        } else {
            // choice.target == null: конец сразу после выбора, без отдельного терминального узла.
            completeProgress(progress, ScenarioOutcome.PARTIAL, now);
            nextNodeResponse = null;
        }

        userProgressRepository.save(progress);

        if (progress.getStatus() == ProgressStatus.COMPLETED) {
            publishCompletion(progress);
        }

        // Режим экзамена: не подсказываем качество решения по ходу прохождения — см. Javadoc
        // ChoiceAppliedResponse. Навигация (status/finalOutcome/nextNode) раскрывается как обычно.
        boolean hideScoreSignal = progress.isExamMode();
        ChoiceAppliedResponse response = new ChoiceAppliedResponse(
                progress.getId(), choice.getId(), choice.getCode(), wasTimeout, progress.getCarClass(),
                hideScoreSignal ? null : appliedLoyaltyDelta, hideScoreSignal ? null : appliedSafetyDelta,
                hideScoreSignal ? null : progress.getLoyaltyScore(), hideScoreSignal ? null : progress.getSafetyScore(),
                progress.getStatus(), progress.getFinalOutcome(), nextNodeResponse);
        eventPublisher.publishEvent(new ProgressStateChangedEvent(progress.getUserId(), response));
        return response;
    }

    /**
     * Шкалы прохождения ограничены {@code [0, 100]} (см. находку code-review: без клампа
     * {@code loyaltyScore}/{@code safetyScore} уходили в отрицательные значения на провальных
     * ветках, а фронтовый {@code ScaleBar} и так предполагает 0-100 и лишь маскировал нарушение
     * инварианта на отображении). Клампится итоговый счёт, а не сырая дельта выбора — поэтому
     * {@code loyaltyDeltaApplied}/{@code safetyDeltaApplied} в истории и в {@link ChoiceAppliedResponse}
     * могут быть меньше по модулю, чем {@link ScenarioChoice#getLoyaltyDelta()}/{@code getSafetyDelta()}
     * seed-данных — это фактически применённый эффект, честный для разбора прохождения.
     */
    private int clampScale(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private void completeProgress(UserProgress progress, ScenarioOutcome outcome, Instant now) {
        progress.setStatus(ProgressStatus.COMPLETED);
        progress.setFinalOutcome(outcome);
        progress.setCurrentNodeId(null);
        progress.setNodeDeadlineAt(null);
        progress.setCompletedAt(now);
    }

    /**
     * Публикуется до коммита транзакции, которая перевела прогресс в COMPLETED (см. Javadoc
     * класса) — слушатели читают {@code @TransactionalEventListener(AFTER_COMMIT)}.
     *
     * <p>Здесь же вычисляются {@link ScenarioCompletedEvent#examMode()} (напрямую из
     * {@link UserProgress#isExamMode()}) и {@link ScenarioCompletedEvent#firstCompletion()} —
     * gamification использует оба флага, чтобы не начислять полные очки/ачивки/челленджи за
     * экзаменационные и повторные прохождения (см. Javadoc полей события).
     */
    private void publishCompletion(UserProgress progress) {
        Scenario scenario = requireScenario(progress.getScenarioId());
        List<ScenarioChoiceHistory> history =
                scenarioChoiceHistoryRepository.findByUserProgressIdOrderBySequenceIndex(progress.getId());

        boolean hadTimeout = history.stream().anyMatch(ScenarioChoiceHistory::isWasTimeout);
        boolean allRoleStepsFollowed = allRoleStepsFollowed(history);
        // progress уже сохранён со status=COMPLETED (см. вызов userProgressRepository.save выше по
        // стеку) — исключаем его собственный id, иначе первое прохождение сочло бы себя повторным.
        boolean firstCompletion = !userProgressRepository.existsByUserIdAndScenarioIdAndStatusAndIdNot(
                progress.getUserId(), progress.getScenarioId(), ProgressStatus.COMPLETED, progress.getId());

        eventPublisher.publishEvent(new ScenarioCompletedEvent(
                progress.getId(),
                progress.getUserId(),
                scenario.getId(),
                scenario.getCode(),
                scenario.getBlock(),
                progress.getFinalOutcome(),
                progress.getLoyaltyScore(),
                progress.getSafetyScore(),
                history.size(),
                hadTimeout,
                allRoleStepsFollowed,
                progress.getStartedAt(),
                progress.getCompletedAt(),
                progress.isExamMode(),
                firstCompletion));
    }

    /**
     * true, если по совокупности всех выборов прохождения хотя бы раз встретился каждый из 4
     * шагов ролевой модели (объединение флагов, не пересечение по одному выбору — см. Javadoc
     * {@link ScenarioCompletedEvent#allRoleStepsFollowed}).
     */
    private boolean allRoleStepsFollowed(List<ScenarioChoiceHistory> history) {
        List<UUID> choiceIds = history.stream().map(ScenarioChoiceHistory::getChoiceId).toList();
        Map<UUID, ScenarioChoice> choicesById = scenarioChoiceRepository.findAllById(choiceIds).stream()
                .collect(Collectors.toMap(ScenarioChoice::getId, c -> c));

        boolean acknowledge = false;
        boolean rule = false;
        boolean solution = false;
        boolean reassure = false;
        for (ScenarioChoiceHistory h : history) {
            ScenarioChoice choice = choicesById.get(h.getChoiceId());
            if (choice == null) {
                continue;
            }
            RoleStepFlags steps = choice.getRoleSteps();
            acknowledge |= steps.isAcknowledge();
            rule |= steps.isRule();
            solution |= steps.isSolution();
            reassure |= steps.isReassure();
        }
        return acknowledge && rule && solution && reassure;
    }

    private UserProgress createProgress(Scenario scenario, UUID playerId, CarClass carClass, UUID examId) {
        ScenarioNode entryNode = requireNode(scenario.getEntryNodeId());
        Instant now = Instant.now();
        UserProgress progress = UserProgress.builder()
                .userId(playerId)
                .scenarioId(scenario.getId())
                .currentNodeId(entryNode.getId())
                .status(ProgressStatus.IN_PROGRESS)
                .carClass(carClass != null ? carClass : CarClass.STANDARD)
                .loyaltyScore(0)
                .safetyScore(0)
                .startedAt(now)
                .updatedAt(now)
                .nodeDeadlineAt(entryNode.getTimerSeconds() != null ? now.plusSeconds(entryNode.getTimerSeconds()) : null)
                .examMode(examId != null)
                .examId(examId)
                .build();
        return userProgressRepository.save(progress);
    }

    private ScenarioChoice findChoiceOnNode(UUID nodeId, UUID choiceId) {
        return scenarioChoiceRepository.findByNodeIdOrderBySortOrder(nodeId).stream()
                .filter(c -> c.getId().equals(choiceId))
                .findFirst()
                .orElseThrow(() -> new ChoiceNotAvailableException(
                        "Выбор '" + choiceId + "' недоступен для текущего узла '" + nodeId + "'"));
    }

    private ProgressStateResponse toProgressState(UserProgress progress, Scenario scenario, ScenarioNode currentNode) {
        NodeStateResponse nodeResponse = null;
        if (currentNode != null) {
            List<ScenarioChoice> choices = currentNode.isTerminal()
                    ? List.of()
                    : scenarioChoiceRepository.findByNodeIdOrderBySortOrder(currentNode.getId());
            nodeResponse = toNodeState(currentNode, choices, progress.getNodeDeadlineAt(), progress.getCarClass());
        }
        return new ProgressStateResponse(
                progress.getId(), scenario.getId(), scenario.getCode(), progress.getStatus(), progress.getCarClass(),
                progress.getLoyaltyScore(), progress.getSafetyScore(), nodeResponse);
    }

    /**
     * {@code carClass} резолвит «портрет пассажира» — переопределение {@link ScenarioNode#getText()}
     * для этого класса вагона, если оно задано в {@code scenario_node_portraits} (см. javadoc
     * {@link ru.vsm.backend.scenario.domain.ScenarioNodePortrait}); иначе используется обычный
     * текст узла, общий для всех классов.
     */
    private NodeStateResponse toNodeState(
            ScenarioNode node, List<ScenarioChoice> choices, Instant deadlineAt, CarClass carClass) {
        List<ChoiceOptionResponse> options = choices.stream()
                .map(c -> new ChoiceOptionResponse(c.getId(), c.getCode(), c.getText()))
                .toList();
        String text = scenarioNodePortraitRepository.findByNodeIdAndCarClass(node.getId(), carClass)
                .map(ScenarioNodePortrait::getText)
                .orElse(node.getText());
        return new NodeStateResponse(
                node.getId(), node.getCode(), node.getNodeType(), text, node.isTerminal(),
                node.getTimerSeconds(), deadlineAt, node.getTerminalOutcome(), node.getOutcomeSummary(), options);
    }

    private ScenarioNode requireNode(UUID nodeId) {
        return scenarioNodeRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalStateException(
                        "Узел '" + nodeId + "' не найден — повреждённые данные графа сценария"));
    }

    private Scenario requireScenario(UUID scenarioId) {
        return scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ScenarioNotFoundException("Сценарий '" + scenarioId + "' не найден"));
    }

    private UserProgress requireProgress(UUID progressId) {
        return userProgressRepository.findById(progressId)
                .orElseThrow(() -> new ProgressNotFoundException("Прохождение '" + progressId + "' не найдено"));
    }

    /**
     * Как {@link #requireProgress}, но с пессимистичной блокировкой строки (см. Javadoc
     * {@link UserProgressRepository#findByIdForUpdate}) — использовать перед любым изменением
     * состояния прохождения ({@code choose}/{@code timeout}), не для read-only чтения.
     */
    private UserProgress requireProgressForUpdate(UUID progressId) {
        return userProgressRepository.findByIdForUpdate(progressId)
                .orElseThrow(() -> new ProgressNotFoundException("Прохождение '" + progressId + "' не найдено"));
    }

    private void checkOwnership(UserProgress progress, UUID playerId) {
        if (!progress.getUserId().equals(playerId)) {
            throw new ProgressAccessDeniedException("Прохождение '" + progress.getId() + "' принадлежит другому игроку");
        }
    }

    private void requireInProgress(UserProgress progress) {
        if (progress.getStatus() != ProgressStatus.IN_PROGRESS) {
            throw new ProgressAlreadyCompletedException(
                    "Прохождение '" + progress.getId() + "' уже завершено (" + progress.getStatus() + ")");
        }
    }

    private boolean isPastDeadline(UserProgress progress) {
        return progress.getNodeDeadlineAt() != null && Instant.now().isAfter(progress.getNodeDeadlineAt());
    }
}
