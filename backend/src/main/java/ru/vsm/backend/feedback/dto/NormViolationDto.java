package ru.vsm.backend.feedback.dto;

/** Частое нарушение норматива: сколько раз игрок выбирал вариант с этой ссылкой на норму */
public record NormViolationDto(String normRef, int count) {
}
