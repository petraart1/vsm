package ru.vsm.backend.scenario.domain;

/** Класс обслуживания вагона ВСМ (см. {@code dataset/standards/sto-rzd-03011-general.md}, */
public enum CarClass {

    /** Базовый класс — модификатор нейтрален, поведение не отличается от прежнего (до этой фичи). */
    STANDARD(1.0, 1.0),
    COMFORT(1.15, 0.9),
    BUSINESS(1.3, 0.85),
    FIRST(1.5, 0.8);

    /** Множитель для отрицательной {@code loyaltyDelta} (ухудшение лояльности). */
    private final double negativeLoyaltyMultiplier;

    /** Множитель для положительной {@code loyaltyDelta} (улучшение лояльности). */
    private final double positiveLoyaltyMultiplier;

    CarClass(double negativeLoyaltyMultiplier, double positiveLoyaltyMultiplier) {
        this.negativeLoyaltyMultiplier = negativeLoyaltyMultiplier;
        this.positiveLoyaltyMultiplier = positiveLoyaltyMultiplier;
    }

    /** Применяет модификатор класса к сырой дельте лояльности выбора. Округление — к ближайшему */
    public int modifyLoyaltyDelta(int rawLoyaltyDelta) {
        if (rawLoyaltyDelta == 0) {
            return 0;
        }
        double multiplier = rawLoyaltyDelta < 0 ? negativeLoyaltyMultiplier : positiveLoyaltyMultiplier;
        return (int) Math.round(rawLoyaltyDelta * multiplier);
    }
}
