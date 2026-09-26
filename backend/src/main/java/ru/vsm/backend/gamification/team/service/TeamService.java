package ru.vsm.backend.gamification.team.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vsm.backend.gamification.domain.CompetencyScore;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.repository.CompetencyScoreRepository;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.team.domain.Team;
import ru.vsm.backend.gamification.team.domain.TeamMembership;
import ru.vsm.backend.gamification.team.repository.TeamMembershipRepository;
import ru.vsm.backend.gamification.team.repository.TeamRepository;
import ru.vsm.backend.gamification.team.web.dto.TeamDto;
import ru.vsm.backend.gamification.team.web.dto.TeamLeaderboardEntryDto;

/**
 * Командный рейтинг (бригады/депо): каталог команд, вступление/смена команды, лидерборд.
 *
 * <p><b>Почему рейтинг сортируется по среднему баллу участника, а не по сумме</b>: сумма даёт
 * преимущество командам с бОльшим числом игроков независимо от их результативности — команда
 * из 20 слабых игроков обгонит команду из 3 сильных просто числом. Средний балл нормирует по
 * размеру состава, поэтому {@link #getLeaderboard()} ранжирует именно по
 * {@link TeamLeaderboardEntryDto#averageScore()}; сумма и число прохождений остаются в ответе
 * как вспомогательные метрики для экрана, но не участвуют в сортировке.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final CompetencyScoreRepository competencyScoreRepository;

    public List<TeamDto> listTeams() {
        return teamRepository.findAll().stream()
                .map(team -> toTeamDto(team, teamMembershipRepository.countByTeamId(team.getId())))
                .toList();
    }

    /**
     * Вступление в команду; смена команды разрешена — повторный вызов с другим {@code teamId}
     * просто обновляет существующее членство игрока, не создавая вторую строку (см.
     * {@code uq_team_membership_player}). Если у игрока ещё нет строки профиля (ни разу не
     * проходил сценарий), она создаётся лениво — тот же приём, что использует
     * {@code GamificationAccrualService} при первом начислении очков, нужен здесь ради FK
     * {@code gamification_team_membership.player_id -> gamification_player_profile.id}.
     */
    @Transactional
    public TeamDto join(UUID teamId, UUID playerId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Команда не найдена: " + teamId));

        if (playerProfileRepository.findById(playerId).isEmpty()) {
            playerProfileRepository.save(PlayerProfile.builder().id(playerId).build());
        }

        TeamMembership membership = teamMembershipRepository.findByPlayerId(playerId)
                .orElseGet(() -> TeamMembership.builder().playerId(playerId).build());
        membership.setTeamId(teamId);
        if (membership.getJoinedAt() == null) {
            membership.setJoinedAt(Instant.now());
        }
        teamMembershipRepository.save(membership);

        return toTeamDto(team, teamMembershipRepository.countByTeamId(teamId));
    }

    /**
     * Рейтинг команд, отсортированный по среднему баллу участника по убыванию (см. javadoc
     * класса). Команда без участников попадает в список с нулями по всем метрикам, а не
     * исключается — состав/приписка видны сразу после сидирования команд.
     */
    public List<TeamLeaderboardEntryDto> getLeaderboard() {
        List<Team> teams = teamRepository.findAll();

        List<TeamLeaderboardEntryDto> unranked = teams.stream()
                .map(this::toLeaderboardEntry)
                .sorted(Comparator.comparingDouble(TeamLeaderboardEntryDto::averageScore).reversed()
                        .thenComparing(TeamLeaderboardEntryDto::code))
                .toList();

        return withRanks(unranked);
    }

    /** Для уведомления о выходе команды на 1-е место — {@code GamificationAccrualService}. */
    public Optional<UUID> findTeamIdByPlayerId(UUID playerId) {
        return teamMembershipRepository.findByPlayerId(playerId).map(TeamMembership::getTeamId);
    }

    /** 1-based позиция команды в рейтинге, или -1, если команда не найдена (например, удалена). */
    public int rankOfTeam(UUID teamId) {
        List<TeamLeaderboardEntryDto> board = getLeaderboard();
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).teamId().equals(teamId)) {
                return i + 1;
            }
        }
        return -1;
    }

    public List<UUID> memberIds(UUID teamId) {
        return teamMembershipRepository.findByTeamId(teamId).stream()
                .map(TeamMembership::getPlayerId)
                .toList();
    }

    public Optional<String> teamName(UUID teamId) {
        return teamRepository.findById(teamId).map(Team::getName);
    }

    private TeamLeaderboardEntryDto toLeaderboardEntry(Team team) {
        List<TeamMembership> members = teamMembershipRepository.findByTeamId(team.getId());
        long memberCount = members.size();

        Map<UUID, PlayerProfile> profilesById = playerProfileRepository
                .findAllById(members.stream().map(TeamMembership::getPlayerId).toList())
                .stream()
                .collect(Collectors.toMap(PlayerProfile::getId, p -> p));

        long totalScore = 0;
        long totalPlaythroughs = 0;
        long totalSafety = 0;
        for (TeamMembership member : members) {
            PlayerProfile profile = profilesById.get(member.getPlayerId());
            if (profile == null) {
                continue;
            }
            totalScore += profile.getTotalScore();
            totalPlaythroughs += profile.getScenariosCompleted();
            totalSafety += competencyScoreRepository.findByPlayerId(member.getPlayerId()).stream()
                    .mapToLong(CompetencyScore::getSafetyPoints)
                    .sum();
        }

        double averageScore = memberCount == 0 ? 0.0 : (double) totalScore / memberCount;
        double averageSafety = memberCount == 0 ? 0.0 : (double) totalSafety / memberCount;

        return new TeamLeaderboardEntryDto(0, team.getId(), team.getCode(), team.getName(), team.getDepot(),
                memberCount, totalScore, averageScore, totalPlaythroughs, averageSafety);
    }

    private List<TeamLeaderboardEntryDto> withRanks(List<TeamLeaderboardEntryDto> ordered) {
        return IntStream.range(0, ordered.size())
                .mapToObj(i -> {
                    TeamLeaderboardEntryDto e = ordered.get(i);
                    return new TeamLeaderboardEntryDto(i + 1L, e.teamId(), e.code(), e.name(), e.depot(),
                            e.memberCount(), e.totalScore(), e.averageScore(), e.totalPlaythroughs(),
                            e.averageSafety());
                })
                .toList();
    }

    private TeamDto toTeamDto(Team team, long memberCount) {
        return new TeamDto(team.getId(), team.getCode(), team.getName(), team.getDepot(), memberCount);
    }
}
