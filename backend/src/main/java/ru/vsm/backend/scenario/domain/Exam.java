package ru.vsm.backend.scenario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Экзамен: набор из нескольких сценариев ({@link ExamScenario}, порядок фиксирован при создании),
 * пройденных подряд без подсказок (см. {@link UserProgress#isExamMode()}), с единым итогом.
 *
 * <p>{@link #playerId} — как и {@link UserProgress#getUserId()}, без FK на пользователя (домен
 * gamification, связь только по id). {@link #carClass} фиксируется на весь экзамен (единый
 * "портрет пассажира" для всех входящих в него сценариев), в отличие от обычного прохождения,
 * где класс выбирается за один сценарий.
 *
 * <p>Агрегаты ({@link #avgLoyaltyScore}/{@link #avgSafetyScore}/{@link #successRate}/{@link #grade})
 * и {@link #finishedAt} заполняются только при переходе {@link #status} в {@link ExamStatus#COMPLETED}
 * (см. {@code ExamService.finishExam}) — до этого момента они {@code null}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "exam")
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "car_class", nullable = false, length = 16)
    @Builder.Default
    private CarClass carClass = CarClass.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ExamStatus status = ExamStatus.IN_PROGRESS;

    /** Сколько сценариев в экзамене — размер {@code exam_scenario} по этому {@code examId}. */
    @Column(nullable = false)
    private int size;

    /** Индекс (0-based, по {@code sortOrder}) первого ещё не завершённого сценария экзамена. */
    @Column(name = "current_index", nullable = false)
    @Builder.Default
    private int currentIndex = 0;

    @Column(name = "avg_loyalty_score")
    private Double avgLoyaltyScore;

    @Column(name = "avg_safety_score")
    private Double avgSafetyScore;

    /** Доля сценариев экзамена с исходом {@link ScenarioOutcome#SUCCESS}, 0..1. */
    @Column(name = "success_rate")
    private Double successRate;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ExamGrade grade;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;
}
