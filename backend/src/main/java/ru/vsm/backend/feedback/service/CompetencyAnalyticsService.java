package ru.vsm.backend.feedback.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import ru.vsm.backend.feedback.dto.BlockCompetencyStatsDto;
import ru.vsm.backend.feedback.dto.CompetencyAnalyticsResponse;
import ru.vsm.backend.feedback.dto.NormViolationDto;
import ru.vsm.backend.feedback.dto.RecommendationReason;
import ru.vsm.backend.feedback.dto.RoleStep;
import ru.vsm.backend.feedback.dto.RoleStepComplianceDto;
import ru.vsm.backend.feedback.dto.ScenarioRecommendationDto;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.RoleStepFlags;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.domain.ScenarioChoice;
import ru.vsm.backend.scenario.domain.ScenarioChoiceHistory;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.domain.UserProgress;
import ru.vsm.backend.scenario.repository.ScenarioChoiceHistoryRepository;
import ru.vsm.backend.scenario.repository.ScenarioChoiceRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.repository.UserProgressRepository;

/**
 * Аналитика компетенций игрока — агрегат по всем его завершённым прохождениям, в отличие от
 * {@link DebriefService}, который разбирает одно прохождение. Как и {@code DebriefService},
 * читает данные сценария строго read-only через репозитории — ничего в таблицы scenario не пишет
 * и своих таблиц не заводит (собирает всё на лету из {@code user_progress} +
 * {@code scenario_choice_history} + {@code scenario_choices}/{@code scenarios} при каждом запросе).
 */
@Service
public class CompetencyAnalyticsService {

    /** Сколько худших блоков максимум попадает в "просевшие компетенции". */
    private static final int MAX_WEAK_COMPETENCIES = 3;
    /** Сколько сценариев максимум отдаём в рекомендациях за один вызов. */
    private static final int MAX_RECOMMENDATIONS = 5;
    /** Сколько нарушений норм максимум отдаём (по убыванию частоты). */
    private static final int MAX_NORM_VIOLATIONS = 5;

    private final UserProgressRepository userProgressRepository;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioChoiceHistoryRepository historyRepository;
    private final ScenarioChoiceRepository scenarioChoiceRepository;

    public CompetencyAnalyticsService(
            UserProgressRepository userProgressRepository,
            ScenarioRepository scenarioRepository,
            ScenarioChoiceHistoryRepository historyRepository,
            ScenarioChoiceRepository scenarioChoiceRepository) {
        this.userProgressRepository = userProgressRepository;
        this.scenarioRepository = scenarioRepository;
        this.historyRepository = historyRepository;
        this.scenarioChoiceRepository = scenarioChoiceRepository;
    }

    public CompetencyAnalyticsResponse analyze(UUID playerId) {
        List<UserProgress> allProgress = userProgressRepository.findByUserId(playerId);
        List<UserProgress> completed = allProgress.stream()
                .filter(p -> p.getStatus() == ProgressStatus.COMPLETED)
                .toList();

        Map<UUID, Scenario> scenarioById = scenarioRepository.findAll().stream()
                .collect(Collectors.toMap(Scenario::getId, s -> s));

        Map<String, List<UserProgress>> completedByBlock = completed.stream()
                .filter(p -> scenarioById.containsKey(p.getScenarioId()))
                .collect(Collectors.groupingBy(p -> scenarioById.get(p.getScenarioId()).getBlock()));

        List<BlockCompetencyStatsDto> blockStatsRaw = completedByBlock.entrySet().stream()
                .map(e -> buildBlockStats(e.getKey(), e.getValue()))
                .toList();

        Set<String> weakBlocks = findWeakCompetencies(blockStatsRaw);

        List<BlockCompetencyStatsDto> blockStats = blockStatsRaw.stream()
                .map(s -> weakBlocks.contains(s.block())
                        ? new BlockCompetencyStatsDto(s.block(), s.playthroughs(), s.avgLoyaltyScore(),
                                s.avgSafetyScore(), s.successRate(), s.failureRate(), true)
                        : s)
                .sorted(Comparator.comparing(BlockCompetencyStatsDto::block))
                .toList();

        List<ScenarioChoice> choicesMade = loadChoicesMade(completed);

        List<RoleStepComplianceDto> roleStepCompliance = buildRoleStepCompliance(choicesMade);
        List<NormViolationDto> normViolations = buildNormViolations(choicesMade);
        List<ScenarioRecommendationDto> recommendations =
                buildRecommendations(playerId, allProgress, completed, scenarioById, weakBlocks);

        return new CompetencyAnalyticsResponse(
                playerId,
                completed.size(),
                blockStats,
                roleStepCompliance,
                normViolations,
                weakBlocks.stream().sorted().toList(),
                recommendations);
    }

    private BlockCompetencyStatsDto buildBlockStats(String block, List<UserProgress> playthroughs) {
        int total = playthroughs.size();
        double avgLoyalty = playthroughs.stream().mapToInt(UserProgress::getLoyaltyScore).average().orElse(0);
        double avgSafety = playthroughs.stream().mapToInt(UserProgress::getSafetyScore).average().orElse(0);
        long successCount = playthroughs.stream().filter(p -> p.getFinalOutcome() == ScenarioOutcome.SUCCESS).count();
        long failureCount = playthroughs.stream().filter(p -> p.getFinalOutcome() == ScenarioOutcome.FAILURE).count();
        return new BlockCompetencyStatsDto(
                block, total, avgLoyalty, avgSafety, (double) successCount / total, (double) failureCount / total, false);
    }

    /**
     * Просевшая компетенция = блок ситуаций, где среди завершённых прохождений игрока
     * {@code blockScore = successRate - failureRate} (диапазон -1..1, доля SUCCESS минус доля
     * FAILURE) наименьший среди блоков, где игрок прошёл хотя бы один сценарий. Берутся до
     * {@value #MAX_WEAK_COMPETENCIES} худших блоков, но только те, где {@code blockScore < 1.0} —
     * если блок пройден исключительно с исходом SUCCESS, он не считается просевшим, даже если
     * формально попадает в "три худших" по отношению к другим идеальным блокам. При равенстве
     * {@code blockScore} блок с большим числом прохождений — более надёжный сигнал и идёт выше;
     * при полном равенстве — по алфавиту кода блока, для детерминированного результата.
     */
    private Set<String> findWeakCompetencies(List<BlockCompetencyStatsDto> blockStats) {
        return blockStats.stream()
                .filter(s -> s.successRate() - s.failureRate() < 1.0)
                .sorted(Comparator
                        .comparingDouble((BlockCompetencyStatsDto s) -> s.successRate() - s.failureRate())
                        .thenComparing(Comparator.comparingInt(BlockCompetencyStatsDto::playthroughs).reversed())
                        .thenComparing(BlockCompetencyStatsDto::block))
                .limit(MAX_WEAK_COMPETENCIES)
                .map(BlockCompetencyStatsDto::block)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Все выборы, фактически сделанные игроком во всех завершённых прохождениях (с дублями). */
    private List<ScenarioChoice> loadChoicesMade(List<UserProgress> completed) {
        List<ScenarioChoiceHistory> allHistory = new ArrayList<>();
        for (UserProgress progress : completed) {
            allHistory.addAll(historyRepository.findByUserProgressIdOrderBySequenceIndex(progress.getId()));
        }
        if (allHistory.isEmpty()) {
            return List.of();
        }
        Set<UUID> choiceIds = allHistory.stream().map(ScenarioChoiceHistory::getChoiceId).collect(Collectors.toSet());
        Map<UUID, ScenarioChoice> choicesById = scenarioChoiceRepository.findAllById(choiceIds).stream()
                .collect(Collectors.toMap(ScenarioChoice::getId, c -> c));
        return allHistory.stream()
                .map(h -> choicesById.get(h.getChoiceId()))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<RoleStepComplianceDto> buildRoleStepCompliance(List<ScenarioChoice> choicesMade) {
        List<RoleStepComplianceDto> result = new ArrayList<>();
        for (RoleStep step : RoleStep.values()) {
            int followed = 0;
            for (ScenarioChoice choice : choicesMade) {
                if (isStepFollowed(step, choice.getRoleSteps())) {
                    followed++;
                }
            }
            int total = choicesMade.size();
            int skipped = total - followed;
            double rate = total == 0 ? 0 : (double) followed / total;
            result.add(new RoleStepComplianceDto(step, step.label(), followed, skipped, rate));
        }
        return result;
    }

    private boolean isStepFollowed(RoleStep step, RoleStepFlags flags) {
        return switch (step) {
            case ACKNOWLEDGE -> flags.isAcknowledge();
            case RULE -> flags.isRule();
            case SOLUTION -> flags.isSolution();
            case REASSURE -> flags.isReassure();
        };
    }

    /** Нарушение нормы = выбор с заполненным normRef и отрицательной дельтой безопасности. */
    private List<NormViolationDto> buildNormViolations(List<ScenarioChoice> choicesMade) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ScenarioChoice choice : choicesMade) {
            if (choice.getNormRef() != null && !choice.getNormRef().isBlank() && choice.getSafetyDelta() < 0) {
                counts.merge(choice.getNormRef(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(e -> new NormViolationDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(NormViolationDto::count).reversed()
                        .thenComparing(NormViolationDto::normRef))
                .limit(MAX_NORM_VIOLATIONS)
                .toList();
    }

    /**
     * Кандидаты на "пройти следующим": в приоритете сценарии просевших компетенций — сперва
     * вообще непройденные ({@link RecommendationReason#NOT_PLAYED}), затем те, где последнее
     * завершённое прохождение закончилось FAILURE/PARTIAL (по одному кандидату на сценарий, самый
     * свежий {@code completedAt}). Если просевших компетенций нет (включая пустое состояние — игрок
     * ещё ничего не проходил), рекомендуются просто первые непройденные активные сценарии по всему
     * каталогу (по {@code situationRefId}), чтобы направить игрока на новый контент.
     */
    private List<ScenarioRecommendationDto> buildRecommendations(
            UUID playerId,
            List<UserProgress> allProgress,
            List<UserProgress> completed,
            Map<UUID, Scenario> scenarioById,
            Set<String> weakBlocks) {

        Set<UUID> attemptedScenarioIds = allProgress.stream().map(UserProgress::getScenarioId).collect(Collectors.toSet());

        Map<UUID, UserProgress> latestCompletedByScenario = new HashMap<>();
        for (UserProgress p : completed) {
            latestCompletedByScenario.merge(p.getScenarioId(), p,
                    (a, b) -> a.getCompletedAt() != null && b.getCompletedAt() != null
                            && a.getCompletedAt().isAfter(b.getCompletedAt()) ? a : b);
        }

        List<Scenario> activeCatalog = scenarioById.values().stream()
                .filter(Scenario::isActive)
                .sorted(Comparator.comparing(Scenario::getSituationRefId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<ScenarioRecommendationDto> recommendations = new ArrayList<>();
        Set<UUID> added = new HashSet<>();

        if (!weakBlocks.isEmpty()) {
            for (String block : weakBlocks) {
                addNotPlayedInBlock(activeCatalog, block, attemptedScenarioIds, added, recommendations);
            }
            for (String block : weakBlocks) {
                addFailedOrPartialInBlock(activeCatalog, block, latestCompletedByScenario, added, recommendations);
            }
        } else {
            for (Scenario scenario : activeCatalog) {
                if (recommendations.size() >= MAX_RECOMMENDATIONS) {
                    break;
                }
                if (!attemptedScenarioIds.contains(scenario.getId()) && added.add(scenario.getId())) {
                    recommendations.add(toRecommendation(scenario, RecommendationReason.NOT_PLAYED));
                }
            }
        }

        return recommendations.stream().limit(MAX_RECOMMENDATIONS).toList();
    }

    private void addNotPlayedInBlock(
            List<Scenario> activeCatalog, String block, Set<UUID> attemptedScenarioIds,
            Set<UUID> added, List<ScenarioRecommendationDto> recommendations) {
        for (Scenario scenario : activeCatalog) {
            if (recommendations.size() >= MAX_RECOMMENDATIONS) {
                return;
            }
            if (scenario.getBlock().equals(block)
                    && !attemptedScenarioIds.contains(scenario.getId())
                    && added.add(scenario.getId())) {
                recommendations.add(toRecommendation(scenario, RecommendationReason.NOT_PLAYED));
            }
        }
    }

    private void addFailedOrPartialInBlock(
            List<Scenario> activeCatalog, String block, Map<UUID, UserProgress> latestCompletedByScenario,
            Set<UUID> added, List<ScenarioRecommendationDto> recommendations) {
        for (Scenario scenario : activeCatalog) {
            if (recommendations.size() >= MAX_RECOMMENDATIONS) {
                return;
            }
            if (!scenario.getBlock().equals(block) || added.contains(scenario.getId())) {
                continue;
            }
            UserProgress lastRun = latestCompletedByScenario.get(scenario.getId());
            if (lastRun == null || lastRun.getFinalOutcome() == null) {
                continue;
            }
            RecommendationReason reason = switch (lastRun.getFinalOutcome()) {
                case FAILURE -> RecommendationReason.FAILED;
                case PARTIAL -> RecommendationReason.PARTIAL;
                case SUCCESS -> null;
            };
            if (reason != null && added.add(scenario.getId())) {
                recommendations.add(toRecommendation(scenario, reason));
            }
        }
    }

    private ScenarioRecommendationDto toRecommendation(Scenario scenario, RecommendationReason reason) {
        return new ScenarioRecommendationDto(scenario.getId(), scenario.getCode(), scenario.getTitle(),
                scenario.getBlock(), reason);
    }
}
