package ru.vsm.backend.scenario.domain;

/**
 * Итоговая оценка экзамена по трём агрегатам всех входящих в него сценариев: доле исходов
 * {@link ScenarioOutcome#SUCCESS} ({@code successRate}, 0..1), средней шкале "рейтинг безопасности"
 * и средней шкале "лояльность пассажира" (обе 0..100, среднее по {@code exam_scenario.safety_score}/
 * {@code loyalty_score} завершённых пунктов экзамена).
 *
 * <p><b>Рейтинг безопасности — определяющий фактор</b>: экзамен нельзя оценить выше
 * {@link #SATISFACTORY}, если средняя безопасность ниже 60 — нарушения норм (см. javadoc
 * {@link ScenarioChoice}: очки безопасности нельзя получить за нарушение норматива) перевешивают
 * лояльность и долю успехов, вне зависимости от их значений.
 *
 * <p>Пороги (оценка — по первой подходящей группе условий сверху вниз):
 * <ul>
 *   <li>{@link #EXCELLENT} — {@code successRate >= 0.8} и {@code avgSafety >= 85} и {@code avgLoyalty >= 70};</li>
 *   <li>{@link #GOOD} — {@code successRate >= 0.6} и {@code avgSafety >= 70} и {@code avgLoyalty >= 55};</li>
 *   <li>{@link #SATISFACTORY} — {@code avgSafety >= 60} и ({@code successRate >= 0.3} или {@code avgLoyalty >= 40});</li>
 *   <li>{@link #UNSATISFACTORY} — иначе (в т.ч. любой {@code avgSafety < 60}).</li>
 * </ul>
 *
 * <p>Согласовано со CHECK-констрейнтом {@code chk_exam_grade}.
 */
public enum ExamGrade {
    EXCELLENT,
    GOOD,
    SATISFACTORY,
    UNSATISFACTORY;

    public static ExamGrade fromResult(double avgLoyalty, double avgSafety, double successRate) {
        if (successRate >= 0.8 && avgSafety >= 85 && avgLoyalty >= 70) {
            return EXCELLENT;
        }
        if (successRate >= 0.6 && avgSafety >= 70 && avgLoyalty >= 55) {
            return GOOD;
        }
        if (avgSafety >= 60 && (successRate >= 0.3 || avgLoyalty >= 40)) {
            return SATISFACTORY;
        }
        return UNSATISFACTORY;
    }
}
