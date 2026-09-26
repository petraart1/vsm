package ru.vsm.backend.scenario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.domain.Exam;
import ru.vsm.backend.scenario.domain.ExamGrade;
import ru.vsm.backend.scenario.domain.ExamScenario;
import ru.vsm.backend.scenario.domain.ExamStatus;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.domain.UserProgress;
import ru.vsm.backend.scenario.repository.ExamRepository;
import ru.vsm.backend.scenario.repository.ExamScenarioRepository;
import ru.vsm.backend.scenario.repository.UserProgressRepository;
import ru.vsm.backend.scenario.web.dto.ExamResponse;
import ru.vsm.backend.scenario.web.dto.ExamScenarioResponse;
import ru.vsm.backend.scenario.web.dto.ProgressStateResponse;

/**
 * Отбор сценариев для экзамена, продвижение по мере завершения пунктов, агрегаты/оценка/слабые
 * блоки при полном завершении, идемпотентность и блокировка разбора (см. Javadoc {@link ExamService}).
 *
 * <p>Полное прохождение всех пунктов экзамена реальными выборами избыточно для проверки
 * агрегации/оценки (граф каждого сценария разный) — {@link ExamService#recordScenarioCompleted}
 * вызывается напрямую (это тот же метод, который в проде вызывает
 * {@code onScenarioCompleted}-слушатель {@code ScenarioCompletedEvent}), а отдельный тест
 * (см. {@code ExamApiIntegrationTest}) проверяет реальную сквозную связку через REST.
 */
@SpringBootTest
@Testcontainers
class ExamServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ExamService examService;
    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private ExamScenarioRepository examScenarioRepository;
    @Autowired
    private UserProgressRepository userProgressRepository;
    @Autowired
    private ru.vsm.backend.scenario.repository.ScenarioRepository scenarioRepository;

    @Test
    void createExamPicksUniqueScenariosFromDistinctBlocksWithTwoOrThreeFlagships() {
        UUID playerId = UUID.randomUUID();

        ExamResponse exam = examService.createExam(playerId, CarClass.STANDARD, 10);

        assertThat(exam.scenarios()).hasSize(10);
        List<UUID> scenarioIds = exam.scenarios().stream().map(ExamScenarioResponse::scenarioId).toList();
        assertThat(scenarioIds).doesNotHaveDuplicates();

        List<String> blocks = exam.scenarios().stream().map(ExamScenarioResponse::block).toList();
        assertThat(blocks).as("датасет знает 10 блоков, экзамен размера 10 должен покрыть их без повтора")
                .doesNotHaveDuplicates();

        long flagshipCount = exam.scenarios().stream().filter(ExamScenarioResponse::flagship).count();
        assertThat(flagshipCount).isBetween(2L, 3L);

        assertThat(exam.status()).isEqualTo(ExamStatus.IN_PROGRESS);
        assertThat(exam.currentIndex()).isZero();
        assertThat(exam.result()).isNull();
    }

    @Test
    void recordScenarioCompletedAdvancesIndexAndIsIdempotentPerScenario() {
        UUID playerId = UUID.randomUUID();
        ExamResponse exam = examService.createExam(playerId, CarClass.STANDARD, 3);
        UUID examId = exam.examId();
        UUID firstScenarioId = exam.scenarios().get(0).scenarioId();

        examService.recordScenarioCompleted(examId, firstScenarioId, ScenarioOutcome.SUCCESS, 80, 90);

        Exam reloaded = examRepository.findById(examId).orElseThrow();
        assertThat(reloaded.getCurrentIndex()).isEqualTo(1);
        assertThat(reloaded.getStatus()).isEqualTo(ExamStatus.IN_PROGRESS);

        // Повтор того же события (например, повторная доставка) не должен продвинуть индекс дважды.
        examService.recordScenarioCompleted(examId, firstScenarioId, ScenarioOutcome.SUCCESS, 80, 90);
        assertThat(examRepository.findById(examId).orElseThrow().getCurrentIndex()).isEqualTo(1);
    }

    @Test
    void finishingAllScenariosComputesAveragesSuccessRateGradeAndWeakBlocks() {
        UUID playerId = UUID.randomUUID();
        ExamResponse exam = examService.createExam(playerId, CarClass.STANDARD, 3);
        UUID examId = exam.examId();
        List<ExamScenarioResponse> items = exam.scenarios();

        // Один провал (безопасность низкая), два успеха с высокими шкалами.
        examService.recordScenarioCompleted(examId, items.get(0).scenarioId(), ScenarioOutcome.FAILURE, 20, 10);
        examService.recordScenarioCompleted(examId, items.get(1).scenarioId(), ScenarioOutcome.SUCCESS, 90, 95);
        examService.recordScenarioCompleted(examId, items.get(2).scenarioId(), ScenarioOutcome.SUCCESS, 80, 90);

        Exam finished = examRepository.findById(examId).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(ExamStatus.COMPLETED);
        assertThat(finished.getFinishedAt()).isNotNull();
        assertThat(finished.getAvgLoyaltyScore()).isEqualTo((20.0 + 90.0 + 80.0) / 3);
        assertThat(finished.getAvgSafetyScore()).isEqualTo((10.0 + 95.0 + 90.0) / 3);
        assertThat(finished.getSuccessRate()).isEqualTo(2.0 / 3);
        // avgSafety = 65 < 70, но >= 60, successRate = 0.67 >= 0.3 => SATISFACTORY (см. ExamGrade).
        assertThat(finished.getGrade()).isEqualTo(ExamGrade.SATISFACTORY);

        ExamResponse reloaded = examService.getExam(examId, playerId);
        assertThat(reloaded.result()).isNotNull();
        assertThat(reloaded.result().weakBlocks()).containsExactly(items.get(0).block());
    }

    @Test
    void assertDebriefAllowedBlocksDuringExamAndAllowsAfterFinishAndForNonExamProgress() {
        UUID playerId = UUID.randomUUID();

        // Прохождение вне экзамена — не ограничено.
        UUID anyScenarioId = scenarioRepository.findByActiveTrue().get(0).getId();
        UserProgress nonExamProgress = userProgressRepository.save(UserProgress.builder()
                .userId(playerId)
                .scenarioId(anyScenarioId)
                .status(ProgressStatus.COMPLETED)
                .build());
        examService.assertDebriefAllowed(nonExamProgress.getId());

        ExamResponse exam = examService.createExam(playerId, CarClass.STANDARD, 2);
        UUID examId = exam.examId();
        List<ExamScenarioResponse> items = exam.scenarios();

        ProgressStateResponse started = examService.startCurrentScenario(examId, playerId);
        assertThatThrownBy(() -> examService.assertDebriefAllowed(started.progressId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));

        // Завершаем оба пункта экзамена напрямую (без реального прохождения — см. Javadoc класса).
        examService.recordScenarioCompleted(examId, items.get(0).scenarioId(), ScenarioOutcome.SUCCESS, 50, 50);
        examService.recordScenarioCompleted(examId, items.get(1).scenarioId(), ScenarioOutcome.SUCCESS, 50, 50);
        assertThat(examRepository.findById(examId).orElseThrow().getStatus()).isEqualTo(ExamStatus.COMPLETED);

        // Теперь разбор того же прохождения (первого пункта) доступен.
        examService.assertDebriefAllowed(started.progressId());
    }

    @Test
    void startCurrentScenarioReturnsSameProgressOnRepeatedCall() {
        UUID playerId = UUID.randomUUID();
        ExamResponse exam = examService.createExam(playerId, CarClass.STANDARD, 2);

        ProgressStateResponse first = examService.startCurrentScenario(exam.examId(), playerId);
        ProgressStateResponse second = examService.startCurrentScenario(exam.examId(), playerId);

        assertThat(second.progressId()).isEqualTo(first.progressId());
        List<ExamScenario> stored = examScenarioRepository.findByExamIdOrderBySortOrder(exam.examId());
        assertThat(stored.get(0).getUserProgressId()).isEqualTo(first.progressId());
    }
}
