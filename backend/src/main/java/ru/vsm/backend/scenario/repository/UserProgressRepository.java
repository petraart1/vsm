package ru.vsm.backend.scenario.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.UserProgress;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {

    List<UserProgress> findByUserId(UUID userId);

    Optional<UserProgress> findByUserIdAndScenarioIdAndStatus(UUID userId, UUID scenarioId, ProgressStatus status);

    /**
     * Как {@link #findById(Object)}, но с {@code SELECT ... FOR UPDATE} (пессимистичная блокировка
     * на запись). Используется {@code ScenarioPlayService.choose}/{@code timeout} перед изменением
     * шкал/узла/статуса — сериализует конкурентные запросы (двойной клик, повторный запрос сети) на
     * одно и то же прохождение: второй запрос блокируется до коммита первого, затем видит уже
     * актуальное состояние (например, {@code status = COMPLETED}) и получает предсказуемую доменную
     * ошибку вместо дублирующей записи в {@code scenario_choice_history} или повторной публикации
     * {@link ru.vsm.backend.scenario.event.ScenarioCompletedEvent}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProgress p where p.id = :id")
    Optional<UserProgress> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Есть ли хоть одно прохождение (в любом статусе) этого сценария. Используется
     * {@code ScenarioSeedService} как guard перед перезаписью графа обновлённой версией:
     * даже {@code COMPLETED}-прохождения держат FK на старые узлы/выборы через
     * {@code scenario_choice_history} (без {@code ON DELETE CASCADE} от scenario_nodes/scenario_choices),
     * поэтому проверка не ограничивается только {@code IN_PROGRESS}.
     */
    boolean existsByScenarioId(UUID scenarioId);

    /**
     * Есть ли у игрока УЖЕ ДРУГОЕ (не {@code excludedProgressId}) завершённое прохождение этого
     * сценария. Используется {@code ScenarioPlayService.publishCompletion} для вычисления
     * {@link ru.vsm.backend.scenario.event.ScenarioCompletedEvent#firstCompletion} —
     * {@code excludedProgressId} исключает само только что завершённое прохождение (его строка
     * в БД уже {@code status = COMPLETED} к моменту вызова), иначе первое прохождение всегда
     * считало бы себя повторным.
     */
    boolean existsByUserIdAndScenarioIdAndStatusAndIdNot(
            UUID userId, UUID scenarioId, ProgressStatus status, UUID excludedProgressId);
}
