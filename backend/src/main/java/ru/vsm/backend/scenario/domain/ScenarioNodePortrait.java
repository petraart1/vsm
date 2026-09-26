package ru.vsm.backend.scenario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Переопределение вводной реплики/текста узла ({@link ScenarioNode#getText()}) для конкретного
 * класса вагона ("портрет пассажира", см. {@link CarClass}) — часть содержимого графа, не отдельный
 * домен: тот же пассажир в узле {@code start} говорит по-разному в вагоне класса "Первый" и
 * "Стандарт", хотя дальнейший граф (выборы, дельты шкал, targets) общий для всех классов.
 *
 * <p>Необязательно: если для (node, carClass) записи нет — используется обычный
 * {@link ScenarioNode#getText()}. Класс {@link CarClass#STANDARD} тоже может иметь собственное
 * переопределение (не обязан совпадать с базовым текстом узла).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scenario_node_portraits")
public class ScenarioNodePortrait {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "car_class", nullable = false, length = 16)
    private CarClass carClass;

    @Column(nullable = false)
    private String text;
}
