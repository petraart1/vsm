package ru.vsm.backend.gamification.admin.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.domain.UserProgress;

/**
 * Read-only агрегаты по {@code user_progress} для статистики администратора
 * (см. {@code ru.vsm.backend.gamification.admin.web.AdminStatsController}).
 *
 * <p>Домен {@code scenario} остаётся источником истины и не меняется этим интерфейсом:
 * репозиторий лежит в {@code gamification.admin} и намеренно расширяет маркерный
 * {@link Repository} (не {@code JpaRepository}) — без методов записи/удаления.
 */
public interface AdminProgressStatsRepository extends Repository<UserProgress, UUID> {

    @Query("select count(p) from UserProgress p")
    long countAllPlaythroughs();

    @Query("select count(distinct p.userId) from UserProgress p")
    long countDistinctPlayers();

    long countByStatus(ProgressStatus status);

    long countByStatusAndFinalOutcome(ProgressStatus status, ScenarioOutcome outcome);

    @Query("select count(distinct p.userId) from UserProgress p where p.updatedAt >= :since")
    long countDistinctPlayersActiveSince(@Param("since") Instant since);

    @Query("select coalesce(avg(p.loyaltyScore), 0) from UserProgress p "
            + "where p.status = ru.vsm.backend.scenario.domain.ProgressStatus.COMPLETED")
    double avgLoyaltyScoreCompleted();

    @Query("select coalesce(avg(p.safetyScore), 0) from UserProgress p "
            + "where p.status = ru.vsm.backend.scenario.domain.ProgressStatus.COMPLETED")
    double avgSafetyScoreCompleted();

    /**
     * По одной строке на сценарий, только для сценариев хотя бы с одним прохождением.
     * Средние шкалы — только по {@code COMPLETED} прохождениям (та же семантика, что у
     * {@link #avgLoyaltyScoreCompleted()}/{@link #avgSafetyScoreCompleted()} в overview);
     * {@code case ... end} без {@code else} даёт {@code NULL} для незавершённых строк,
     * которые {@code avg} по стандарту SQL просто игнорирует.
     */
    @Query("""
            select new ru.vsm.backend.gamification.admin.repository.ScenarioAggregateRow(
                p.scenarioId,
                count(p),
                sum(case when p.status = ru.vsm.backend.scenario.domain.ProgressStatus.COMPLETED then 1L else 0L end),
                sum(case when p.finalOutcome = ru.vsm.backend.scenario.domain.ScenarioOutcome.SUCCESS then 1L else 0L end),
                avg(case when p.status = ru.vsm.backend.scenario.domain.ProgressStatus.COMPLETED then p.loyaltyScore end),
                avg(case when p.status = ru.vsm.backend.scenario.domain.ProgressStatus.COMPLETED then p.safetyScore end))
            from UserProgress p
            group by p.scenarioId
            """)
    List<ScenarioAggregateRow> aggregateByScenario();

    /**
     * По одной строке на блок ситуаций (join по {@code scenarioId} к таблице сценариев).
     * Средние шкалы — только по {@code COMPLETED} прохождениям, см. {@link #aggregateByScenario()}.
     */
    @Query("""
            select new ru.vsm.backend.gamification.admin.repository.BlockAggregateRow(
                s.block,
                count(p),
                sum(case when p.status = ru.vsm.backend.scenario.domain.ProgressStatus.COMPLETED then 1L else 0L end),
                sum(case when p.finalOutcome = ru.vsm.backend.scenario.domain.ScenarioOutcome.SUCCESS then 1L else 0L end),
                avg(case when p.status = ru.vsm.backend.scenario.domain.ProgressStatus.COMPLETED then p.loyaltyScore end),
                avg(case when p.status = ru.vsm.backend.scenario.domain.ProgressStatus.COMPLETED then p.safetyScore end))
            from UserProgress p, ru.vsm.backend.scenario.domain.Scenario s
            where p.scenarioId = s.id
            group by s.block
            """)
    List<BlockAggregateRow> aggregateByBlock();

    /**
     * По одной строке на игрока ({@code userId}), для экспорта статистики администратора
     * (см. {@code AdminStatsService#getPlayerStats()}). Средние шкалы и {@code successCount} —
     * та же семантика, что у {@link #aggregateByScenario()}; {@code lastActivity} — момент
     * последнего обновления любого прохождения игрока, независимо от статуса.
     */
    @Query("""
            select new ru.vsm.backend.gamification.admin.repository.PlayerAggregateRow(
                p.userId,
                count(p),
                sum(case when p.status = ru.vsm.backend.scenario.domain.ProgressStatus.COMPLETED then 1L else 0L end),
                sum(case when p.finalOutcome = ru.vsm.backend.scenario.domain.ScenarioOutcome.SUCCESS then 1L else 0L end),
                avg(case when p.status = ru.vsm.backend.scenario.domain.ProgressStatus.COMPLETED then p.loyaltyScore end),
                avg(case when p.status = ru.vsm.backend.scenario.domain.ProgressStatus.COMPLETED then p.safetyScore end),
                max(p.updatedAt))
            from UserProgress p
            group by p.userId
            """)
    List<PlayerAggregateRow> aggregateByPlayer();
}
