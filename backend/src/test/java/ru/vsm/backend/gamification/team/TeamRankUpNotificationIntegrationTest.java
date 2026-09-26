package ru.vsm.backend.gamification.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.service.NotificationService;
import ru.vsm.backend.gamification.team.domain.Team;
import ru.vsm.backend.gamification.team.repository.TeamRepository;
import ru.vsm.backend.gamification.team.service.TeamService;
import ru.vsm.backend.gamification.web.dto.NotificationDto;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;

/**
 * Уведомление {@link NotificationType#TEAM_RANK_UP} всем участникам команды при выходе команды
 * на 1-е место командного рейтинга (см. {@code GamificationAccrualService#evaluateTeamRankUp}).
 */
@SpringBootTest
@Testcontainers
class TeamRankUpNotificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private TeamService teamService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private Team newTeam(String suffix) {
        return teamRepository.save(Team.builder()
                .code("r-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Бригада " + suffix)
                .depot("Москва Ленинградская")
                .build());
    }

    private void setScore(UUID playerId, int totalScore) {
        PlayerProfile profile = playerProfileRepository.findById(playerId).orElseThrow();
        profile.setTotalScore(totalScore);
        playerProfileRepository.save(profile);
    }

    @Test
    void allMembersNotifiedWhenTeamOvertakesFirstPlace() {
        Team leadTeam = newTeam("lead");
        Team risingTeam = newTeam("rising");

        UUID leadPlayer = UUID.randomUUID();
        teamService.join(leadTeam.getId(), leadPlayer);
        setScore(leadPlayer, 50);

        UUID risingPlayer = UUID.randomUUID();
        teamService.join(risingTeam.getId(), risingPlayer);

        // Средний балл leadTeam (50) выше нуля у остальных, включая risingTeam — leadTeam на 1-м месте.
        assertThat(teamService.rankOfTeam(leadTeam.getId())).isEqualTo(1);
        assertThat(teamService.rankOfTeam(risingTeam.getId())).isGreaterThan(1);

        // SUCCESS: base 100 + loyaltyGain 10 + safetyGain 10 = 120 очков одним прохождением —
        // средний балл risingTeam (120) обгоняет leadTeam (50).
        eventPublisher.publishEvent(new ScenarioCompletedEvent(
                UUID.randomUUID(), risingPlayer, UUID.randomUUID(), "scenario-rankup", "boarding",
                ScenarioOutcome.SUCCESS, 10, 10, 2, false, true,
                Instant.now().minusSeconds(30), Instant.now(), false, true));

        assertThat(teamService.rankOfTeam(risingTeam.getId())).isEqualTo(1);
        assertThat(teamService.rankOfTeam(leadTeam.getId())).isGreaterThan(1);

        List<NotificationDto> notifications = notificationService.list(risingPlayer, false);
        assertThat(notifications).extracting(NotificationDto::type)
                .contains(NotificationType.TEAM_RANK_UP.name());
    }

    @Test
    void noNotificationWhenTeamWasAlreadyFirst() {
        Team team = newTeam("already-first");
        UUID playerId = UUID.randomUUID();
        teamService.join(team.getId(), playerId);
        // Заведомо выше очков любой другой команды в этом тестовом классе (общая БД на класс,
        // тесты не изолированы транзакцией) — гарантирует 1-е место независимо от порядка тестов.
        setScore(playerId, 1000);

        assertThat(teamService.rankOfTeam(team.getId())).isEqualTo(1);

        // Ещё одно прохождение — команда и так уже #1, TEAM_RANK_UP не должен повториться.
        eventPublisher.publishEvent(new ScenarioCompletedEvent(
                UUID.randomUUID(), playerId, UUID.randomUUID(), "scenario-rankup-2", "boarding",
                ScenarioOutcome.PARTIAL, 5, 5, 1, false, true,
                Instant.now().minusSeconds(30), Instant.now(), false, true));

        List<NotificationDto> notifications = notificationService.list(playerId, false);
        assertThat(notifications).extracting(NotificationDto::type)
                .doesNotContain(NotificationType.TEAM_RANK_UP.name());
    }
}
