package ru.vsm.backend.feedback.dto;

/**
 * Частое нарушение норматива: сколько раз игрок выбирал вариант с этой ссылкой на норму
 * ({@link ru.vsm.backend.scenario.domain.ScenarioChoice#getNormRef()}), у которого при этом
 * отрицательная дельта безопасности — то есть выбор нарушает норму, а не просто её упоминает.
 *
 * @param normRef ссылка на норматив как заполнена в данных выбора
 * @param count   сколько раз выбран за все завершённые прохождения игрока
 */
public record NormViolationDto(String normRef, int count) {
}
