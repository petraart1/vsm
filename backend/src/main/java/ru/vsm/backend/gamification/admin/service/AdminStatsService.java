package ru.vsm.backend.gamification.admin.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.gamification.admin.repository.AdminChoiceHistoryStatsRepository;
import ru.vsm.backend.gamification.admin.repository.AdminProgressStatsRepository;
import ru.vsm.backend.gamification.admin.repository.BlockAggregateRow;
import ru.vsm.backend.gamification.admin.repository.ChoiceTimeoutRow;
import ru.vsm.backend.gamification.admin.repository.PlayerAggregateRow;
import ru.vsm.backend.gamification.admin.repository.ScenarioAggregateRow;
import ru.vsm.backend.gamification.admin.web.dto.BlockStatsEntryDto;
import ru.vsm.backend.gamification.admin.web.dto.OverviewStatsResponse;
import ru.vsm.backend.gamification.admin.web.dto.PlayerStatsEntryDto;
import ru.vsm.backend.gamification.admin.web.dto.ScenarioStatsEntryDto;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.team.domain.Team;
import ru.vsm.backend.gamification.team.domain.TeamMembership;
import ru.vsm.backend.gamification.team.repository.TeamMembershipRepository;
import ru.vsm.backend.gamification.team.repository.TeamRepository;
import ru.vsm.backend.gamification.team.service.TeamService;
import ru.vsm.backend.gamification.team.web.dto.TeamLeaderboardEntryDto;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.repository.ScenarioRepository;

/**
 * Только чтение: агрегированная статистика для административной панели (JSON и CSV-экспорт —
 * см. {@code AdminStatsController}). Начисление очков этот сервис не трогает — он лишь читает
 * {@code scenario}/{@code gamification} таблицы через read-only репозитории домена (см.
 * {@code AdminProgressStatsRepository}, {@code AdminChoiceHistoryStatsRepository}), уже
 * существующий {@link ScenarioRepository} и командный рейтинг ({@link TeamService},
 * {@code gamification.team}).
 *
 * <p>Доступ ограничен ролью {@code ADMIN} на уровне {@code /api/admin/**} (см. {@code SecurityConfig}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatsService {

    private final AdminProgressStatsRepository progressStatsRepository;
    private final AdminChoiceHistoryStatsRepository choiceHistoryStatsRepository;
    private final ScenarioRepository scenarioRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final TeamRepository teamRepository;
    private final TeamService teamService;

    public OverviewStatsResponse getOverview() {
        long totalPlayers = progressStatsRepository.countDistinctPlayers();
        long totalPlaythroughs = progressStatsRepository.countAllPlaythroughs();
        long completed = progressStatsRepository.countByStatus(ProgressStatus.COMPLETED);
        long inProgress = progressStatsRepository.countByStatus(ProgressStatus.IN_PROGRESS);

        long success = progressStatsRepository.countByStatusAndFinalOutcome(
                ProgressStatus.COMPLETED, ScenarioOutcome.SUCCESS);
        long partial = progressStatsRepository.countByStatusAndFinalOutcome(
                ProgressStatus.COMPLETED, ScenarioOutcome.PARTIAL);
        long failure = progressStatsRepository.countByStatusAndFinalOutcome(
                ProgressStatus.COMPLETED, ScenarioOutcome.FAILURE);

        Instant now = Instant.now();
        long active24h = progressStatsRepository.countDistinctPlayersActiveSince(now.minus(Duration.ofHours(24)));
        long active7d = progressStatsRepository.countDistinctPlayersActiveSince(now.minus(Duration.ofDays(7)));

        return new OverviewStatsResponse(
                totalPlayers,
                totalPlaythroughs,
                completed,
                inProgress,
                progressStatsRepository.avgLoyaltyScoreCompleted(),
                progressStatsRepository.avgSafetyScoreCompleted(),
                rate(success, completed),
                rate(partial, completed),
                rate(failure, completed),
                active24h,
                active7d);
    }

    public List<ScenarioStatsEntryDto> getScenarioStats() {
        List<ScenarioAggregateRow> rows = progressStatsRepository.aggregateByScenario();

        Map<UUID, ChoiceTimeoutRow> timeoutsByScenario = choiceHistoryStatsRepository
                .aggregateTimeoutsByScenario().stream()
                .collect(Collectors.toMap(ChoiceTimeoutRow::scenarioId, Function.identity()));

        Map<UUID, Scenario> scenariosById = scenarioRepository
                .findAllById(rows.stream().map(ScenarioAggregateRow::scenarioId).toList()).stream()
                .collect(Collectors.toMap(Scenario::getId, Function.identity()));

        return rows.stream()
                .map(row -> toScenarioStatsEntry(row, scenariosById.get(row.scenarioId()),
                        timeoutsByScenario.get(row.scenarioId())))
                // самые "проваливаемые" (низкий successRate) — первыми, дальше по числу прохождений
                .sorted(Comparator.comparingDouble(ScenarioStatsEntryDto::successRate)
                        .thenComparing(Comparator.comparingLong(ScenarioStatsEntryDto::totalPlaythroughs).reversed()))
                .toList();
    }

    public List<BlockStatsEntryDto> getBlockStats() {
        return progressStatsRepository.aggregateByBlock().stream()
                .map(this::toBlockStatsEntry)
                .sorted(Comparator.comparing(BlockStatsEntryDto::block))
                .toList();
    }

    private ScenarioStatsEntryDto toScenarioStatsEntry(
            ScenarioAggregateRow row, Scenario scenario, ChoiceTimeoutRow timeouts) {
        long total = orZero(row.totalPlaythroughs());
        long completed = orZero(row.completedCount());
        long success = orZero(row.successCount());
        long totalChoices = timeouts == null ? 0 : orZero(timeouts.totalChoices());
        long timeoutChoices = timeouts == null ? 0 : orZero(timeouts.timeoutChoices());

        return new ScenarioStatsEntryDto(
                row.scenarioId(),
                scenario == null ? null : scenario.getCode(),
                scenario == null ? null : scenario.getTitle(),
                scenario == null ? null : scenario.getBlock(),
                total,
                completed,
                rate(success, completed),
                orZero(row.avgLoyalty()),
                orZero(row.avgSafety()),
                rate(timeoutChoices, totalChoices));
    }

    /**
     * По одной строке на игрока хотя бы с одним прохождением (независимо от статуса).
     * {@code teamName} собирается одним групповым запросом по всем командам/членствам, а не
     * поштучным поиском на игрока — не N+1, несмотря на то что {@link TeamMembershipRepository}
     * не даёт метода "найти команды по списку игроков". Отсортировано по {@code totalScore}
     * по убыванию, как личный лидерборд ({@code GamificationQueryService#getLeaderboard}).
     */
    public List<PlayerStatsEntryDto> getPlayerStats() {
        List<PlayerAggregateRow> rows = progressStatsRepository.aggregateByPlayer();

        Map<UUID, PlayerProfile> profilesById = playerProfileRepository
                .findAllById(rows.stream().map(PlayerAggregateRow::playerId).toList()).stream()
                .collect(Collectors.toMap(PlayerProfile::getId, Function.identity()));

        Map<UUID, UUID> teamIdByPlayer = teamMembershipRepository.findAll().stream()
                .collect(Collectors.toMap(TeamMembership::getPlayerId, TeamMembership::getTeamId));
        Map<UUID, String> teamNameById = teamRepository.findAll().stream()
                .collect(Collectors.toMap(Team::getId, Team::getName));

        return rows.stream()
                .map(row -> toPlayerStatsEntry(row, profilesById.get(row.playerId()),
                        teamNameById.get(teamIdByPlayer.get(row.playerId()))))
                .sorted(Comparator.comparingInt(PlayerStatsEntryDto::totalScore).reversed())
                .toList();
    }

    /** Командный рейтинг — переиспользует {@link TeamService#getLeaderboard()}, без своего запроса. */
    public List<TeamLeaderboardEntryDto> getTeamStats() {
        return teamService.getLeaderboard();
    }

    private PlayerStatsEntryDto toPlayerStatsEntry(PlayerAggregateRow row, PlayerProfile profile, String teamName) {
        long completed = orZero(row.completedCount());
        long success = orZero(row.successCount());
        int totalScore = profile == null ? 0 : profile.getTotalScore();
        String displayName = profile != null && profile.getDisplayName() != null && !profile.getDisplayName().isBlank()
                ? profile.getDisplayName()
                : generatedDisplayName(row.playerId());

        return new PlayerStatsEntryDto(
                row.playerId(),
                displayName,
                teamName,
                totalScore,
                orZero(row.totalPlaythroughs()),
                rate(success, completed),
                orZero(row.avgLoyalty()),
                orZero(row.avgSafety()),
                row.lastActivity());
    }

    /** Та же заглушка имени, что и {@code GamificationQueryService#generatedDisplayName} — домена аутентификации нет. */
    private static String generatedDisplayName(UUID playerId) {
        String tail = playerId.toString().replace("-", "");
        return "Проводник-" + tail.substring(tail.length() - 4).toUpperCase();
    }

    private BlockStatsEntryDto toBlockStatsEntry(BlockAggregateRow row) {
        long completed = orZero(row.completedCount());
        long success = orZero(row.successCount());
        return new BlockStatsEntryDto(
                row.block(),
                orZero(row.totalPlaythroughs()),
                completed,
                rate(success, completed),
                orZero(row.avgLoyalty()),
                orZero(row.avgSafety()));
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
