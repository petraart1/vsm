package ru.vsm.backend.gamification.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.team.domain.Team;
import ru.vsm.backend.gamification.team.repository.TeamMembershipRepository;
import ru.vsm.backend.gamification.team.repository.TeamRepository;
import ru.vsm.backend.gamification.team.service.TeamService;
import ru.vsm.backend.gamification.team.web.dto.TeamDto;
import ru.vsm.backend.gamification.team.web.dto.TeamLeaderboardEntryDto;

/**
 * Вступление/смена команды и рейтинг команд по среднему баллу участника (см. javadoc
 * {@link TeamService} — почему по среднему, а не по сумме).
 */
@SpringBootTest
@Testcontainers
class TeamServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private TeamService teamService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMembershipRepository teamMembershipRepository;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    private Team newTeam(String suffix) {
        return teamRepository.save(Team.builder()
                .code("t-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Тестовая бригада " + suffix)
                .depot("Москва Ленинградская")
                .build());
    }

    @Test
    void joinCreatesMembershipAndLazyPlayerProfile() {
        Team team = newTeam("A");
        UUID playerId = UUID.randomUUID();
        assertThat(playerProfileRepository.findById(playerId)).isEmpty();

        TeamDto result = teamService.join(team.getId(), playerId);

        assertThat(result.id()).isEqualTo(team.getId());
        assertThat(result.memberCount()).isEqualTo(1);
        assertThat(teamMembershipRepository.findByPlayerId(playerId))
                .hasValueSatisfying(m -> assertThat(m.getTeamId()).isEqualTo(team.getId()));
        // FK gamification_team_membership.player_id -> gamification_player_profile.id: строка
        // профиля должна быть создана лениво, иначе вставка членства не прошла бы вовсе.
        assertThat(playerProfileRepository.findById(playerId)).isPresent();
    }

    @Test
    void joinAgainWithDifferentTeamSwitchesMembershipInPlace() {
        Team teamA = newTeam("B1");
        Team teamB = newTeam("B2");
        UUID playerId = UUID.randomUUID();

        teamService.join(teamA.getId(), playerId);
        TeamDto afterSwitch = teamService.join(teamB.getId(), playerId);

        assertThat(afterSwitch.id()).isEqualTo(teamB.getId());
        assertThat(teamMembershipRepository.findByPlayerId(playerId))
                .hasValueSatisfying(m -> assertThat(m.getTeamId()).isEqualTo(teamB.getId()));
        // Одна строка на игрока, не две — старое членство обновлено, не задублировано.
        assertThat(teamMembershipRepository.findAll().stream()
                .filter(m -> m.getPlayerId().equals(playerId)).count()).isEqualTo(1);
        assertThat(teamRepository.findById(teamA.getId())).isPresent();
    }

    private void setScore(UUID playerId, int totalScore, int scenariosCompleted) {
        PlayerProfile profile = playerProfileRepository.findById(playerId).orElseThrow();
        profile.setTotalScore(totalScore);
        profile.setScenariosCompleted(scenariosCompleted);
        playerProfileRepository.save(profile);
    }

    @Test
    void leaderboardRanksByAverageScoreNotSum() {
        Team bigWeakTeam = newTeam("C-big");
        Team smallStrongTeam = newTeam("C-small");

        // Большая команда: 3 игрока по 10 очков (сумма 30, среднее 10).
        for (int i = 0; i < 3; i++) {
            UUID playerId = UUID.randomUUID();
            teamService.join(bigWeakTeam.getId(), playerId);
            setScore(playerId, 10, 1);
        }
        // Маленькая команда: 1 игрок с 100 очками (сумма 100, среднее 100) — сумма больше у
        // первой не в счёт, средний балл выше у второй.
        UUID strongPlayer = UUID.randomUUID();
        teamService.join(smallStrongTeam.getId(), strongPlayer);
        setScore(strongPlayer, 100, 2);

        List<TeamLeaderboardEntryDto> board = teamService.getLeaderboard();

        TeamLeaderboardEntryDto bigEntry = board.stream()
                .filter(e -> e.teamId().equals(bigWeakTeam.getId())).findFirst().orElseThrow();
        TeamLeaderboardEntryDto smallEntry = board.stream()
                .filter(e -> e.teamId().equals(smallStrongTeam.getId())).findFirst().orElseThrow();

        assertThat(bigEntry.totalScore()).isEqualTo(30);
        assertThat(bigEntry.averageScore()).isEqualTo(10.0);
        assertThat(bigEntry.totalPlaythroughs()).isEqualTo(3);

        assertThat(smallEntry.totalScore()).isEqualTo(100);
        assertThat(smallEntry.averageScore()).isEqualTo(100.0);

        assertThat(smallEntry.rank()).isLessThan(bigEntry.rank());
        assertThat(teamService.rankOfTeam(smallStrongTeam.getId()))
                .isLessThan(teamService.rankOfTeam(bigWeakTeam.getId()));
    }

    @Test
    void emptyTeamHasZeroedLeaderboardEntry() {
        Team emptyTeam = newTeam("D-empty");

        TeamLeaderboardEntryDto entry = teamService.getLeaderboard().stream()
                .filter(e -> e.teamId().equals(emptyTeam.getId())).findFirst().orElseThrow();

        assertThat(entry.memberCount()).isZero();
        assertThat(entry.totalScore()).isZero();
        assertThat(entry.averageScore()).isZero();
        assertThat(entry.totalPlaythroughs()).isZero();
        assertThat(entry.averageSafety()).isZero();
    }

    @Test
    void listTeamsIncludesSeededCatalogWithMemberCounts() {
        List<TeamDto> teams = teamService.listTeams();
        // TeamSeeder засеивает 5 бригад на старте приложения (идемпотентно) — плюс любые
        // тестовые команды, созданные другими тестами в этом же контексте.
        assertThat(teams.size()).isGreaterThanOrEqualTo(5);
        assertThat(teams).allSatisfy(t -> assertThat(t.memberCount()).isGreaterThanOrEqualTo(0));
    }
}
