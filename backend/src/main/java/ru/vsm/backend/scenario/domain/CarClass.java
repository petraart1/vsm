package ru.vsm.backend.scenario.domain;

/**
 * Класс обслуживания вагона ВСМ (см. {@code dataset/standards/sto-rzd-03011-general.md},
 * раздел «Классы обслуживания»): Стандарт / Комфорт / Бизнес / Первый.
 *
 * <p><b>Идея «портрет пассажира»</b>: один и тот же сценарий и один и тот же выбор проводника
 * по-разному влияют на лояльность пассажира в зависимости от того, в вагоне какого класса это
 * происходит — чем выше класс обслуживания, тем выше базовые ожидания пассажира, поэтому:
 * <ul>
 *   <li>неудачный/грубый ответ проигрывает в лояльности сильнее ({@link #negativeLoyaltyMultiplier}
 *       {@code > 1} для верхних классов) — пассажир первого класса прощает меньше;</li>
 *   <li>хороший ответ выигрывает в лояльности слабее ({@link #positiveLoyaltyMultiplier}
 *       {@code < 1} для верхних классов) — качественный сервис там воспринимается как норма,
 *       а не как приятный сюрприз.</li>
 * </ul>
 *
 * <p>Модификатор применяется только к {@code loyaltyDelta} — рейтинг безопасности объективен
 * и не должен зависеть от класса обслуживания (см. javadoc {@link ScenarioChoice}: нельзя
 * давать очки безопасности за нарушение норматива, вне зависимости от класса вагона).
 * Применяется до клампинга шкалы на {@code [0, 100]} в {@code ScenarioPlayService}.
 */
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

    /**
     * Применяет модификатор класса к сырой дельте лояльности выбора. Округление — к ближайшему
     * целому от нуля не требуется отдельно: {@code Math.round} всегда округляет к ближайшему,
     * знак сохраняется, т.к. множители положительны, а дельта может быть 0 (модификатор не
     * меняет ноль).
     */
    public int modifyLoyaltyDelta(int rawLoyaltyDelta) {
        if (rawLoyaltyDelta == 0) {
            return 0;
        }
        double multiplier = rawLoyaltyDelta < 0 ? negativeLoyaltyMultiplier : positiveLoyaltyMultiplier;
        return (int) Math.round(rawLoyaltyDelta * multiplier);
    }
}
