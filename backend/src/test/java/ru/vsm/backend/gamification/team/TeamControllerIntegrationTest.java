package ru.vsm.backend.gamification.team;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.gamification.team.domain.Team;
import ru.vsm.backend.gamification.team.repository.TeamRepository;
import ru.vsm.backend.gamification.team.web.TeamController;

/**
 * REST: GET /api/gamification/teams, POST .../{id}/join, GET /api/gamification/leaderboard/teams.
 * Логика подсчёта среднего/пустой команды — {@link TeamServiceIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TeamControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TeamRepository teamRepository;

    private Team newTeam(String suffix) {
        return teamRepository.save(Team.builder()
                .code("w-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Тестовая бригада " + suffix)
                .depot("Санкт-Петербург Московский")
                .build());
    }

    @Test
    void listTeamsIncludesSeededCatalog() throws Exception {
        mockMvc.perform(get("/api/gamification/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))));
    }

    @Test
    void joinReturnsUpdatedTeamWithMemberCount() throws Exception {
        Team team = newTeam("join");
        UUID playerId = UUID.randomUUID();

        mockMvc.perform(post("/api/gamification/teams/{id}/join", team.getId())
                        .header(TeamController.PLAYER_ID_HEADER, playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(team.getId().toString()))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void joinUnknownTeamReturns404() throws Exception {
        mockMvc.perform(post("/api/gamification/teams/{id}/join", UUID.randomUUID())
                        .header(TeamController.PLAYER_ID_HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void joinWithInvalidPlayerIdHeaderReturns400() throws Exception {
        Team team = newTeam("bad-header");

        mockMvc.perform(post("/api/gamification/teams/{id}/join", team.getId())
                        .header(TeamController.PLAYER_ID_HEADER, "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teamLeaderboardIsOrderedByRankAscending() throws Exception {
        mockMvc.perform(get("/api/gamification/leaderboard/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1));
    }
}
