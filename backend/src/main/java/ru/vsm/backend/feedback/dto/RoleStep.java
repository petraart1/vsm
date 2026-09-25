package ru.vsm.backend.feedback.dto;

/**
 * Универсальная 4-шаговая ролевая модель ответа проводника (см.
 * {@code dataset/scenarios/situations-onboard.md}, раздел "Универсальная ролевая модель"):
 * признать -> обозначить правило -> предложить решение -> заверить. Подписи и примеры реплик
 * взяты дословно оттуда — используются для пояснений в разборе прохождения (debrief).
 */
public enum RoleStep {

    ACKNOWLEDGE("Признать ситуацию", "«Я Вас понимаю…», «Понимаю, что ситуация неприятная…»"),
    RULE("Обозначить правило", "«Обращаю Ваше внимание, что…», «Информирую Вас о том, что…»"),
    SOLUTION("Предложить решение", "«Я уточню и вернусь к Вам…», «Я приглашу начальника поезда…»"),
    REASSURE("Заверить", "«Благодарю Вас за понимание…», «Благодарю за обращение…»");

    private final String label;
    private final String examplePhrase;

    RoleStep(String label, String examplePhrase) {
        this.label = label;
        this.examplePhrase = examplePhrase;
    }

    public String label() {
        return label;
    }

    public String examplePhrase() {
        return examplePhrase;
    }
}
