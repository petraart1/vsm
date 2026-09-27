package ru.vsm.backend.gamification.admin.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import ru.vsm.backend.scenario.domain.ScenarioChoiceHistory;

/**
 * Read-only агрегат таймаутов по {@code scenario_choice_history} для статистики
 * администратора. Маркерный {@link Repository} — без методов записи.
 */
public interface AdminChoiceHistoryStatsRepository extends Repository<ScenarioChoiceHistory, UUID> {

    /** По одной строке на сценарий: всего сделанных выборов (включая примененные по таймауту) */
    @Query("""
            select new ru.vsm.backend.gamification.admin.repository.ChoiceTimeoutRow(
                up.scenarioId,
                count(h),
                sum(case when h.wasTimeout = true then 1L else 0L end))
            from ScenarioChoiceHistory h, ru.vsm.backend.scenario.domain.UserProgress up
            where h.userProgressId = up.id
            group by up.scenarioId
            """)
    List<ChoiceTimeoutRow> aggregateTimeoutsByScenario();
}
