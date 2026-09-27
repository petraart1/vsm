package ru.vsm.backend.scenario.domain;

/** Итоговая оценка экзамена по трём агрегатам всех входящих в него сценариев: доле исходов */
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
