package ru.vsm.backend.scenario.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.domain.Exam;
import ru.vsm.backend.scenario.domain.ExamGrade;
import ru.vsm.backend.scenario.domain.ExamScenario;
import ru.vsm.backend.scenario.domain.ExamStatus;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.domain.UserProgress;
import ru.vsm.backend.scenario.event.ExamCompletedEvent;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;
import ru.vsm.backend.scenario.repository.ExamRepository;
import ru.vsm.backend.scenario.repository.ExamScenarioRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.repository.UserProgressRepository;
import ru.vsm.backend.scenario.service.exception.ExamAlreadyFinishedException;
import ru.vsm.backend.scenario.service.exception.ExamNotFoundException;
import ru.vsm.backend.scenario.service.exception.ProgressAccessDeniedException;
import ru.vsm.backend.scenario.web.dto.ExamResponse;
import ru.vsm.backend.scenario.web.dto.ExamResultResponse;
import ru.vsm.backend.scenario.web.dto.ExamScenarioResponse;
import ru.vsm.backend.scenario.web.dto.ProgressStateResponse;

/**
 * Режим экзамена: набор из нескольких активных сценариев из разных блоков (2-3 из них —
 * флагманские), пройденных подряд без раскрытия дельт/шкал по ходу (см. Javadoc
 * {@code ChoiceAppliedResponse}), с единым итогом по завершении.
 *
 * <p><b>Отбор сценариев</b> ({@link #createExam}): сначала распределяются 2-3 флагманских
 * сценария по случайным различным блокам, затем остаток мест — по одному случайному сценарию из
 * оставшихся ещё не занятых блоков; если размер экзамена больше числа блоков датасета, остаток
 * добирается случайными ещё не выбранными сценариями из общего пула (уникальность
 * {@code scenarioId} в рамках одного экзамена гарантирована всегда, уникальность блока — пока
 * хватает различных блоков).
 *
 * <p><b>Прохождение</b> — обычный {@link ScenarioPlayService}: {@link #startCurrentScenario}
 * лишь создаёт/возвращает {@code UserProgress} текущего пункта экзамена с
 * {@code examMode=true}/{@code examId}, дальше игрок ходит по нему через
 * {@code POST /api/scenarios/progress/{id}/choices/{choiceId}} как обычно.
 *
 * <p><b>Учёт завершения пункта</b> ({@link #recordScenarioCompleted}) не требует прямой
 * зависимости от {@link ScenarioPlayService} (которая, наоборот, зависит от этого сервиса через
 * {@link #startCurrentScenario} — циклическая зависимость сервисов не заводится): отдельный бин
 * {@link ru.vsm.backend.scenario.event.ExamCompletionListener} слушает уже существующий
 * {@link ScenarioCompletedEvent}, публикуемый на каждое завершение прохождения, достаёт
 * {@code examId} из связанного {@link UserProgress} — если он не {@code null}, значит это
 * завершение относится к экзамену — и вызывает {@link #recordScenarioCompleted} этого сервиса
 * (через Spring-прокси, не self-invocation — см. Javadoc метода).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    /** Число блоков датасета, из которых допускается набрать экзамен без повторов блока (10). */
    private static final int MIN_FLAGSHIP_COUNT = 2;
    private static final int MAX_FLAGSHIP_COUNT = 3;
    private static final int DEFAULT_SIZE = 10;

    private final ScenarioRepository scenarioRepository;
    private final ExamRepository examRepository;
    private final ExamScenarioRepository examScenarioRepository;
    private final UserProgressRepository userProgressRepository;
    private final ScenarioPlayService scenarioPlayService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Создаёт новый экзамен для игрока. {@code size} — сколько сценариев в экзамене ({@link #DEFAULT_SIZE}
     * по умолчанию, см. {@code ExamController}); {@code carClass} — единый "портрет пассажира" для
     * всех сценариев этого экзамена ({@code STANDARD}, если не задан).
     */
    @Transactional
    public ExamResponse createExam(UUID playerId, CarClass carClass, Integer size) {
        int requestedSize = size != null ? size : DEFAULT_SIZE;
        if (requestedSize <= 0) {
            throw new IllegalArgumentException("size должен быть положительным, получено: " + requestedSize);
        }

        List<Scenario> active = scenarioRepository.findByActiveTrue();
        if (active.isEmpty()) {
            throw new IllegalStateException("Нет ни одного активного сценария — экзамен невозможен");
        }
        List<Scenario> selected = pickExamScenarios(active, requestedSize);

        Exam exam = examRepository.save(Exam.builder()
                .playerId(playerId)
                .carClass(carClass != null ? carClass : CarClass.STANDARD)
                .status(ExamStatus.IN_PROGRESS)
                .size(selected.size())
                .currentIndex(0)
                .startedAt(Instant.now())
                .build());

        List<ExamScenario> items = new ArrayList<>(selected.size());
        for (int i = 0; i < selected.size(); i++) {
            Scenario s = selected.get(i);
            items.add(ExamScenario.builder()
                    .examId(exam.getId())
                    .sortOrder(i)
                    .scenarioId(s.getId())
                    .block(s.getBlock())
                    .flagship(s.isFlagship())
                    .completed(false)
                    .build());
        }
        examScenarioRepository.saveAll(items);

        return toResponse(exam, items);
    }

    /**
     * Набирает {@code size} сценариев из разных блоков (2-3 флагманских) — см. Javadoc класса.
     * Порядок результата перемешан (не сгруппирован флагман-первыми/по блокам).
     */
    private List<Scenario> pickExamScenarios(List<Scenario> active, int size) {
        Map<String, List<Scenario>> byBlock = active.stream().collect(Collectors.groupingBy(Scenario::getBlock));
        List<String> blocks = new ArrayList<>(byBlock.keySet());
        Collections.shuffle(blocks);

        List<Scenario> flagshipPool = active.stream().filter(Scenario::isFlagship).toList();
        int flagshipTarget = Math.min(
                size, Math.min(flagshipPool.size(),
                        ThreadLocalRandom.current().nextInt(MIN_FLAGSHIP_COUNT, MAX_FLAGSHIP_COUNT + 1)));

        List<Scenario> selected = new ArrayList<>();
        Set<String> usedBlocks = new HashSet<>();
        Set<UUID> usedScenarios = new HashSet<>();

        // Проход 1: флагманы, по одному на различный блок.
        for (String block : blocks) {
            if (selected.size() >= flagshipTarget) {
                break;
            }
            List<Scenario> flagshipsInBlock = byBlock.get(block).stream().filter(Scenario::isFlagship).toList();
            if (!flagshipsInBlock.isEmpty()) {
                Scenario chosen = flagshipsInBlock.get(ThreadLocalRandom.current().nextInt(flagshipsInBlock.size()));
                selected.add(chosen);
                usedBlocks.add(block);
                usedScenarios.add(chosen.getId());
            }
        }

        // Проход 2: остаток мест — по одному случайному сценарию из ещё не занятых блоков.
        // Предпочитает нефлагманские сценарии блока (флагман уже учтён/пропущен проходом 1) —
        // иначе итоговое число флагманов могло бы случайно превысить flagshipTarget.
        List<String> remainingBlocks = blocks.stream().filter(b -> !usedBlocks.contains(b)).toList();
        for (String block : remainingBlocks) {
            if (selected.size() >= size) {
                break;
            }
            List<Scenario> candidates = pickNonFlagshipFirst(byBlock.get(block), usedScenarios);
            if (!candidates.isEmpty()) {
                Scenario chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
                selected.add(chosen);
                usedBlocks.add(block);
                usedScenarios.add(chosen.getId());
            }
        }

        // Проход 3: если блоков не хватило на весь размер экзамена — добираем случайными ещё не
        // выбранными сценариями из общего пула (блоки уже могут повторяться, id сценариев — нет),
        // также предпочитая нефлагманские, пока они есть.
        if (selected.size() < size) {
            List<Scenario> pool = new ArrayList<>(pickNonFlagshipFirst(active, usedScenarios));
            Collections.shuffle(pool);
            for (Scenario s : pool) {
                if (selected.size() >= size) {
                    break;
                }
                selected.add(s);
                usedScenarios.add(s.getId());
            }
        }

        Collections.shuffle(selected);
        return selected;
    }

    /**
     * Кандидаты из {@code candidatePool}, ещё не занятые ({@code usedScenarios}) — нефлагманские,
     * если такие есть, иначе (весь пул исчерпан флагманами) любые оставшиеся. Держит итоговое
     * число флагманов экзамена равным ровно тому, что отобрал проход 1 (см. Javadoc
     * {@link #pickExamScenarios}), а не "минимум 2-3".
     */
    private List<Scenario> pickNonFlagshipFirst(List<Scenario> candidatePool, Set<UUID> usedScenarios) {
        List<Scenario> nonFlagship = candidatePool.stream()
                .filter(s -> !usedScenarios.contains(s.getId()) && !s.isFlagship())
                .toList();
        if (!nonFlagship.isEmpty()) {
            return nonFlagship;
        }
        return candidatePool.stream().filter(s -> !usedScenarios.contains(s.getId())).toList();
    }

    @Transactional(readOnly = true)
    public ExamResponse getExam(UUID examId, UUID playerId) {
        Exam exam = requireExam(examId);
        checkOwnership(exam, playerId);
        List<ExamScenario> items = examScenarioRepository.findByExamIdOrderBySortOrder(examId);
        return toResponse(exam, items);
    }

    /**
     * Начинает (или возвращает уже начатое) прохождение первого не пройденного пункта экзамена —
     * тем же {@link ScenarioPlayService#start(UUID, UUID, CarClass, UUID)}, что и обычный старт, но с
     * {@code examId}, чтобы прохождение получило {@code examMode=true}.
     */
    @Transactional
    public ProgressStateResponse startCurrentScenario(UUID examId, UUID playerId) {
        Exam exam = requireExam(examId);
        checkOwnership(exam, playerId);
        if (exam.getStatus() != ExamStatus.IN_PROGRESS) {
            throw new ExamAlreadyFinishedException("Экзамен '" + examId + "' уже завершён");
        }

        ExamScenario current = examScenarioRepository.findByExamIdOrderBySortOrder(examId).stream()
                .filter(item -> !item.isCompleted())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Экзамен '" + examId + "' не завершён, но не имеет незавершённых пунктов"));

        if (current.getUserProgressId() != null) {
            return scenarioPlayService.getProgress(current.getUserProgressId(), playerId);
        }

        ProgressStateResponse response =
                scenarioPlayService.start(current.getScenarioId(), playerId, exam.getCarClass(), examId);
        current.setUserProgressId(response.progressId());
        examScenarioRepository.save(current);
        return response;
    }

    /**
     * Точка входа для разбора прохождения ({@code DebriefService.buildDebrief}, читает-только
     * зависимость из feedback в scenario, симметрично уже существующим прямым обращениям
     * feedback к репозиториям scenario): разбор недоступен, пока экзамен, к которому относится это
     * прохождение, не завершён целиком — иначе игрок получил бы подсказку по текущему пункту
     * экзамена, разобрав уже пройденный. Прохождения вне экзамена ({@code examId == null}) не
     * ограничены.
     */
    @Transactional(readOnly = true)
    public void assertDebriefAllowed(UUID userProgressId) {
        UserProgress progress = userProgressRepository.findById(userProgressId).orElse(null);
        if (progress == null || progress.getExamId() == null) {
            return;
        }
        Exam exam = examRepository.findById(progress.getExamId()).orElse(null);
        if (exam != null && exam.getStatus() == ExamStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Разбор недоступен до завершения экзамена '" + exam.getId() + "'");
        }
    }

    /**
     * Вызывается только из {@link ru.vsm.backend.scenario.event.ExamCompletionListener}
     * (отдельный бин, {@code @TransactionalEventListener(AFTER_COMMIT)} на
     * {@link ScenarioCompletedEvent}) — НЕ напрямую другим методом этого же класса.
     *
     * <p><b>{@code propagation = REQUIRES_NEW}, а не self-invocation этим же классом</b>: раньше
     * этот метод вызывался как {@code this.recordScenarioCompleted(...)} из
     * {@code @TransactionalEventListener}-метода этого же {@code ExamService} — тот же баг-паттерн,
     * что подробно разобран в javadoc {@code GamificationAccrualService#processEvent}: Spring-прокси
     * с {@code @Transactional} перехватывает только вызовы ИЗВНЕ бина; self-invocation идёт в обход
     * прокси как обычный вызов метода того же объекта, поэтому объявленная пропагация вообще не
     * применялась — метод присоединялся к уже закоммиченной (умирающей) транзакции AFTER_COMMIT
     * колбэка, `SELECT`ы отрабатывали, но `save()` физически не коммитились. Экзамен из-за этого
     * нельзя было пройти целиком: пункт никогда не помечался завершённым, {@code currentIndex} не
     * рос, итоговая оценка не считалась — без единой строки в логе. Вынос слушателя в отдельный бин
     * ({@link ru.vsm.backend.scenario.event.ExamCompletionListener}) исключает self-invocation:
     * вызов {@code examService.recordScenarioCompleted(...)} идёт через Spring-прокси этого бина, и
     * {@code REQUIRES_NEW} гарантирует отдельную физическую транзакцию, не зависящую от уже
     * закоммиченных ресурсов исходной.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordScenarioCompleted(
            UUID examId, UUID scenarioId, ScenarioOutcome outcome, int loyaltyScore, int safetyScore) {
        Exam exam = examRepository.findById(examId).orElse(null);
        if (exam == null || exam.getStatus() != ExamStatus.IN_PROGRESS) {
            return;
        }
        ExamScenario item = examScenarioRepository.findByExamIdAndScenarioId(examId, scenarioId).orElse(null);
        if (item == null || item.isCompleted()) {
            // Идемпотентность: повторная доставка события (см. аналогичный принцип в
            // GamificationAccrualService) не должна дважды продвинуть currentIndex/пересчитать итог.
            return;
        }

        item.setCompleted(true);
        item.setOutcome(outcome);
        item.setLoyaltyScore(loyaltyScore);
        item.setSafetyScore(safetyScore);
        examScenarioRepository.save(item);

        exam.setCurrentIndex(exam.getCurrentIndex() + 1);
        List<ExamScenario> items = examScenarioRepository.findByExamIdOrderBySortOrder(examId);
        boolean allCompleted = items.stream().allMatch(ExamScenario::isCompleted);
        if (allCompleted) {
            finishExam(exam, items);
        } else {
            examRepository.save(exam);
        }
    }

    private void finishExam(Exam exam, List<ExamScenario> items) {
        double avgLoyalty = items.stream().mapToInt(ExamScenario::getLoyaltyScore).average().orElse(0);
        double avgSafety = items.stream().mapToInt(ExamScenario::getSafetyScore).average().orElse(0);
        long successCount = items.stream().filter(i -> i.getOutcome() == ScenarioOutcome.SUCCESS).count();
        double successRate = items.isEmpty() ? 0 : (double) successCount / items.size();
        ExamGrade grade = ExamGrade.fromResult(avgLoyalty, avgSafety, successRate);
        List<String> weakBlocks = computeWeakBlocks(items);

        Instant now = Instant.now();
        exam.setStatus(ExamStatus.COMPLETED);
        exam.setFinishedAt(now);
        exam.setAvgLoyaltyScore(avgLoyalty);
        exam.setAvgSafetyScore(avgSafety);
        exam.setSuccessRate(successRate);
        exam.setGrade(grade);
        examRepository.save(exam);

        log.info("Экзамен {} завершён игроком {}: оценка {}, successRate {}", exam.getId(), exam.getPlayerId(),
                grade, successRate);

        eventPublisher.publishEvent(new ExamCompletedEvent(
                exam.getId(), exam.getPlayerId(), avgLoyalty, avgSafety, successRate, grade, weakBlocks,
                exam.getStartedAt(), now));
    }

    /**
     * Блоки, где пункт экзамена не завершился {@code SUCCESS}, от худшего (по сумме шкал) к менее
     * слабому, без повторов блока.
     */
    private List<String> computeWeakBlocks(List<ExamScenario> items) {
        return items.stream()
                .filter(i -> i.getOutcome() != ScenarioOutcome.SUCCESS)
                .sorted(Comparator.comparingInt(i -> i.getLoyaltyScore() + i.getSafetyScore()))
                .map(ExamScenario::getBlock)
                .distinct()
                .toList();
    }

    private ExamResponse toResponse(Exam exam, List<ExamScenario> items) {
        Map<UUID, Scenario> scenariosById = scenarioRepository
                .findAllById(items.stream().map(ExamScenario::getScenarioId).toList()).stream()
                .collect(Collectors.toMap(Scenario::getId, s -> s));

        List<ExamScenarioResponse> scenarioResponses = items.stream()
                .map(item -> {
                    Scenario s = scenariosById.get(item.getScenarioId());
                    return new ExamScenarioResponse(
                            item.getSortOrder(), item.getScenarioId(), s != null ? s.getCode() : null,
                            item.getBlock(), s != null ? s.getTitle() : null, item.isFlagship(),
                            item.getUserProgressId(), item.isCompleted(), item.getOutcome(),
                            item.getLoyaltyScore(), item.getSafetyScore());
                })
                .toList();

        ExamResultResponse result = exam.getStatus() == ExamStatus.COMPLETED
                ? new ExamResultResponse(
                        exam.getAvgLoyaltyScore(), exam.getAvgSafetyScore(), exam.getSuccessRate(),
                        exam.getGrade(), computeWeakBlocks(items))
                : null;

        return new ExamResponse(
                exam.getId(), exam.getPlayerId(), exam.getCarClass(), exam.getStatus(), exam.getSize(),
                exam.getCurrentIndex(), exam.getStartedAt(), exam.getFinishedAt(), scenarioResponses, result);
    }

    private Exam requireExam(UUID examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new ExamNotFoundException("Экзамен '" + examId + "' не найден"));
    }

    private void checkOwnership(Exam exam, UUID playerId) {
        if (!exam.getPlayerId().equals(playerId)) {
            throw new ProgressAccessDeniedException("Экзамен '" + exam.getId() + "' принадлежит другому игроку");
        }
    }
}
