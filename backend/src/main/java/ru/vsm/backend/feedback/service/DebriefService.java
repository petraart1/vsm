package ru.vsm.backend.feedback.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.vsm.backend.feedback.dto.DebriefResponse;
import ru.vsm.backend.feedback.dto.DebriefStepDto;
import ru.vsm.backend.feedback.dto.KeyMomentDto;
import ru.vsm.backend.feedback.dto.RoleStep;
import ru.vsm.backend.scenario.domain.NodeType;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.domain.ScenarioChoice;
import ru.vsm.backend.scenario.domain.ScenarioChoiceHistory;
import ru.vsm.backend.scenario.domain.ScenarioNode;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.domain.UserProgress;
import ru.vsm.backend.scenario.repository.ScenarioChoiceHistoryRepository;
import ru.vsm.backend.scenario.repository.ScenarioChoiceRepository;
import ru.vsm.backend.scenario.repository.ScenarioNodeRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.repository.UserProgressRepository;

/**
 * Строит разбор прохождения сценария (см. {@code design/screens/debrief.md}) по id
 * {@code user_progress}. Читает данные сценария строго read-only через репозитории —
 * ничего в таблицы scenario не пишет.
 *
 * <p>Своих таблиц для MVP не заводит: таймлайн и объяснения собираются на лету из
 * {@code scenario_choice_history} + графа сценария при каждом запросе. Если понадобится
 * кэширование по многим прохождениям — тогда появится смысл в отдельных таблицах.
 */
@Service
public class DebriefService {

    private final UserProgressRepository userProgressRepository;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioNodeRepository scenarioNodeRepository;
    private final ScenarioChoiceRepository scenarioChoiceRepository;
    private final ScenarioChoiceHistoryRepository historyRepository;
    private final ExplanationResolver explanationResolver;

    public DebriefService(
            UserProgressRepository userProgressRepository,
            ScenarioRepository scenarioRepository,
            ScenarioNodeRepository scenarioNodeRepository,
            ScenarioChoiceRepository scenarioChoiceRepository,
            ScenarioChoiceHistoryRepository historyRepository,
            ExplanationResolver explanationResolver) {
        this.userProgressRepository = userProgressRepository;
        this.scenarioRepository = scenarioRepository;
        this.scenarioNodeRepository = scenarioNodeRepository;
        this.scenarioChoiceRepository = scenarioChoiceRepository;
        this.historyRepository = historyRepository;
        this.explanationResolver = explanationResolver;
    }

    public DebriefResponse buildDebrief(UUID userProgressId) {
        UserProgress progress = userProgressRepository.findById(userProgressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Прохождение не найдено: " + userProgressId));
        Scenario scenario = scenarioRepository.findById(progress.getScenarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Сценарий прохождения не найден: " + progress.getScenarioId()));
        List<ScenarioChoiceHistory> history =
                historyRepository.findByUserProgressIdOrderBySequenceIndex(userProgressId);

        Map<UUID, ScenarioNode> nodesById = scenarioNodeRepository
                .findAllById(history.stream().map(ScenarioChoiceHistory::getNodeId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ScenarioNode::getId, n -> n));
        Map<UUID, ScenarioChoice> choicesById = scenarioChoiceRepository
                .findAllById(history.stream().map(ScenarioChoiceHistory::getChoiceId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ScenarioChoice::getId, c -> c));

        List<DebriefStepDto> timeline = new ArrayList<>();
        Set<String> normReferences = new LinkedHashSet<>();
        KeyMomentDto keyMoment = null;
        int bestGap = 0;

        for (ScenarioChoiceHistory entry : history) {
            ScenarioNode node = nodesById.get(entry.getNodeId());
            ScenarioChoice choice = choicesById.get(entry.getChoiceId());
            if (node == null || choice == null) {
                // История ссылается на узел/выбор, которого больше нет в графе (например, seed
                // сценария был пересобран после того, как прохождение уже стартовало) — пропускаем
                // шаг вместо падения 500, разбор остаётся полезным по остальным шагам.
                continue;
            }

            // Все варианты этого узла нужны и для ключевой развилки ниже, и для распознавания
            // скрытой механики закадровой коммуникации (эскалация, которую пассажир не слышит) —
            // считаем один раз на шаг.
            List<ScenarioChoice> alternatives = scenarioChoiceRepository.findByNodeIdOrderBySortOrder(node.getId());
            boolean hiddenCommunicationEffect = isHiddenCommunicationEffect(node, alternatives);

            ExplanationResolver.Explanation explanation =
                    explanationResolver.resolve(choice, node.getNodeType(), entry.isWasTimeout());
            normReferences.addAll(explanation.normReferences());

            List<RoleStep> completed = ExplanationResolver.completedSteps(choice.getRoleSteps());
            List<RoleStep> skipped = ExplanationResolver.skippedSteps(choice.getRoleSteps());
            boolean scaleConflict = ExplanationResolver.isScaleConflict(choice.getLoyaltyDelta(), choice.getSafetyDelta());

            timeline.add(new DebriefStepDto(
                    entry.getSequenceIndex(),
                    node.getCode(),
                    node.getText(),
                    node.getNodeType(),
                    choice.getCode(),
                    choice.getText(),
                    entry.isWasTimeout(),
                    entry.getLoyaltyDeltaApplied(),
                    entry.getSafetyDeltaApplied(),
                    completed.stream().map(RoleStep::label).toList(),
                    skipped.stream().map(RoleStep::label).toList(),
                    scaleConflict,
                    explanation.text(),
                    hiddenCommunicationEffect));

            // Ключевая развилка: сравниваем фактический выбор со всеми альтернативами в том же
            // узле по суммарному эффекту на обе шкалы (равный вес) — где разрыв с лучшей
            // альтернативой максимален, там и была решающая ошибка.
            int chosenScore = choice.getLoyaltyDelta() + choice.getSafetyDelta();
            ScenarioChoice best = choice;
            int bestScore = chosenScore;
            for (ScenarioChoice alt : alternatives) {
                int altScore = alt.getLoyaltyDelta() + alt.getSafetyDelta();
                if (altScore > bestScore) {
                    bestScore = altScore;
                    best = alt;
                }
            }
            int gap = bestScore - chosenScore;
            if (gap > bestGap) {
                bestGap = gap;
                String advice = buildAdvice(node, choice, best);
                String betterExplanation = explanationResolver.resolve(best, node.getNodeType(), false).text();
                keyMoment = new KeyMomentDto(
                        entry.getSequenceIndex(),
                        node.getText(),
                        choice.getText(),
                        choice.getLoyaltyDelta(),
                        choice.getSafetyDelta(),
                        best.getText(),
                        best.getLoyaltyDelta(),
                        best.getSafetyDelta(),
                        advice,
                        betterExplanation);
            }
        }

        boolean interrupted = progress.getStatus() == ProgressStatus.ABANDONED;
        String verdict = buildVerdict(progress, interrupted);
        String summary = keyMoment == null
                ? buildPraise(timeline)
                : "Ключевая развилка: \"" + truncate(keyMoment.nodeText()) + "\". "
                        + keyMoment.adviceText();

        return new DebriefResponse(
                progress.getId(),
                scenario.getId(),
                scenario.getCode(),
                scenario.getTitle(),
                scenario.getBlock(),
                progress.getStatus(),
                progress.getFinalOutcome(),
                verdict,
                interrupted,
                progress.getLoyaltyScore(),
                progress.getSafetyScore(),
                timeline,
                keyMoment,
                summary,
                normReferences.stream().toList());
    }

    /**
     * Распознаёт узел со скрытой механикой закадровой коммуникации (например, разговор по
     * служебной рации, который сам пассажир не слышит) без привязки к конкретному сценарию/узлу:
     * узел эскалации, где у всех альтернатив одна и та же дельта лояльности (пассажир не видит
     * разницы), но дельта безопасности различается (формулировка всё равно на неё влияет — риск
     * случайно быть услышанным, нарушение протокола переговоров и т.п.). Ключевая деталь для
     * разбора: решение здесь совсем не отражается на реакции пассажира, только на безопасности.
     */
    private boolean isHiddenCommunicationEffect(ScenarioNode node, List<ScenarioChoice> alternatives) {
        if (node.getNodeType() != NodeType.ESCALATION || alternatives.size() < 2) {
            return false;
        }
        int firstLoyalty = alternatives.get(0).getLoyaltyDelta();
        boolean sameLoyaltyAcrossAlternatives =
                alternatives.stream().allMatch(c -> c.getLoyaltyDelta() == firstLoyalty);
        boolean safetyDiffersAcrossAlternatives =
                alternatives.stream().map(ScenarioChoice::getSafetyDelta).distinct().count() > 1;
        return sameLoyaltyAcrossAlternatives && safetyDiffersAcrossAlternatives;
    }

    private String buildAdvice(ScenarioNode node, ScenarioChoice chosen, ScenarioChoice better) {
        List<RoleStep> betterCompleted = ExplanationResolver.completedSteps(better.getRoleSteps());
        List<RoleStep> chosenCompleted = ExplanationResolver.completedSteps(chosen.getRoleSteps());
        List<RoleStep> missingInChosen = betterCompleted.stream()
                .filter(s -> !chosenCompleted.contains(s))
                .toList();

        StringBuilder advice = new StringBuilder();
        advice.append("Более сильный вариант в этом узле — «").append(better.getText()).append("»");
        if (!missingInChosen.isEmpty()) {
            advice.append(" — в нём соблюдены шаги ролевой модели (")
                    .append(missingInChosen.stream().map(RoleStep::label).collect(Collectors.joining(", ")))
                    .append("), которых не хватило в выбранном ответе");
        }
        advice.append(". Эффект на шкалы: лояльность ")
                .append(formatDelta(better.getLoyaltyDelta()))
                .append(", безопасность ")
                .append(formatDelta(better.getSafetyDelta()))
                .append(" против фактических ")
                .append(formatDelta(chosen.getLoyaltyDelta()))
                .append(" и ")
                .append(formatDelta(chosen.getSafetyDelta()))
                .append(" у выбранного ответа.");
        return advice.toString();
    }

    private String buildPraise(List<DebriefStepDto> timeline) {
        boolean allStepsCovered = timeline.stream().allMatch(s -> s.roleStepsSkipped().isEmpty());
        if (timeline.isEmpty()) {
            return "Прохождение не содержит решений — разбор недоступен.";
        }
        if (allStepsCovered) {
            return "Отличное прохождение: на каждой развилке выбран вариант не хуже лучшего "
                    + "по эффекту на шкалы, все 4 шага ролевой модели соблюдены там, где применимо.";
        }
        return "Прохождение близко к оптимальному по эффекту на шкалы: на каждой развилке "
                + "выбран вариант не хуже лучшего, хотя отдельные шаги ролевой модели "
                + "(признать/обозначить правило/предложить решение/заверить) были пропущены — "
                + "см. пометки в таймлайне.";
    }

    private String buildVerdict(UserProgress progress, boolean interrupted) {
        if (interrupted) {
            return "Прохождение завершено автоматически";
        }
        if (progress.getStatus() == ProgressStatus.IN_PROGRESS) {
            return "Прохождение ещё не завершено";
        }
        ScenarioOutcome outcome = progress.getFinalOutcome();
        if (outcome == null) {
            return "Прохождение завершено";
        }
        return switch (outcome) {
            case SUCCESS -> "Хорошо справились";
            case PARTIAL -> "Есть над чем поработать";
            case FAILURE -> "Критическая ошибка безопасности";
        };
    }

    private static String formatDelta(int delta) {
        return delta >= 0 ? "+" + delta : String.valueOf(delta);
    }

    private static String truncate(String text) {
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }
}
