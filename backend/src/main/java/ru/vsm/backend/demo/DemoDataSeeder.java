package ru.vsm.backend.demo;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.team.domain.Team;
import ru.vsm.backend.gamification.team.domain.TeamMembership;
import ru.vsm.backend.gamification.team.repository.TeamMembershipRepository;
import ru.vsm.backend.gamification.team.repository.TeamRepository;
import ru.vsm.backend.scenario.domain.CarClass;
import ru.vsm.backend.scenario.domain.ProgressStatus;
import ru.vsm.backend.scenario.domain.Scenario;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import ru.vsm.backend.scenario.repository.UserProgressRepository;
import ru.vsm.backend.scenario.service.ScenarioPlayService;
import ru.vsm.backend.scenario.web.dto.ChoiceOptionResponse;

/**
 * Seeds demo data for showcase and testing: 12 demo players with realistic playthroughs of
 * various scenarios. Runs only when profile "demo" is active.
 *
 * <p>Idempotent: if demo players already exist, re-run does nothing.
 */
@Slf4j
@Component
@Profile("demo")
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private final PlayerProfileRepository playerProfileRepository;
    private final ScenarioRepository scenarioRepository;
    private final UserProgressRepository userProgressRepository;
    private final ScenarioPlayService scenarioPlayService;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    // Fixed seed for reproducible demo behavior
    private static final long DEMO_SEED = 42L;
    private static final int DEMO_PLAYER_COUNT = 12;
    private static final CarClass[] CAR_CLASSES = CarClass.values();

    private static final String[] DEMO_PLAYER_NAMES = {
            "Проводник Иванов",
            "Проводник Петров",
            "Проводник Сидоров",
            "Проводник Козлов",
            "Проводник Новиков",
            "Проводник Орлов",
            "Проводник Лебедев",
            "Проводник Дмитриев",
            "Проводник Эмелин",
            "Проводник Белов",
            "Проводник Морозов",
            "Проводник Волков"
    };

    private static final UUID[] DEMO_PLAYER_UUIDS = {
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            UUID.fromString("00000000-0000-0000-0000-000000000005"),
            UUID.fromString("00000000-0000-0000-0000-000000000006"),
            UUID.fromString("00000000-0000-0000-0000-000000000007"),
            UUID.fromString("00000000-0000-0000-0000-000000000008"),
            UUID.fromString("00000000-0000-0000-0000-000000000009"),
            UUID.fromString("00000000-0000-0000-0000-00000000000a"),
            UUID.fromString("00000000-0000-0000-0000-00000000000b"),
            UUID.fromString("00000000-0000-0000-0000-00000000000c")
    };

    @Override
    public void run(ApplicationArguments args) {
        // Check if demo data already exists (check if first demo player profile exists)
        if (playerProfileRepository.existsById(DEMO_PLAYER_UUIDS[0])) {
            log.info("Demo data already exists, skipping seeding");
            return;
        }

        log.info("Seeding demo data: {} players", DEMO_PLAYER_COUNT);
        Random random = new Random(DEMO_SEED);

        List<Scenario> scenarios = scenarioRepository.findAll();
        if (scenarios.isEmpty()) {
            log.warn("No scenarios available, skipping demo data seeding");
            return;
        }

        for (int i = 0; i < DEMO_PLAYER_COUNT; i++) {
            UUID playerId = DEMO_PLAYER_UUIDS[i];
            String displayName = DEMO_PLAYER_NAMES[i];

            seedPlayerPlaythroughs(playerId, displayName, scenarios, random);
        }

        assignPlayersToTeams();

        log.info("Demo data seeding completed");
    }

    /**
     * Распределяет фиксированных демо-игроков по командам (round-robin), для наглядного
     * командного рейтинга в демо. Выполняется после сидирования прохождений, чтобы у игроков,
     * успевших набрать очки, они уже учитывались; игрокам без единого завершённого прохождения
     * (например, если все попытки в {@link #playScenario} завершились исключением) строка
     * профиля создаётся здесь же — иначе внешний ключ {@code gamification_team_membership} не
     * даст сохранить членство. Идемпотентно — уже привязанный игрок пропускается.
     */
    private void assignPlayersToTeams() {
        List<Team> teams = teamRepository.findAll();
        if (teams.isEmpty()) {
            log.warn("No teams available, skipping demo team assignment");
            return;
        }

        for (int i = 0; i < DEMO_PLAYER_UUIDS.length; i++) {
            UUID playerId = DEMO_PLAYER_UUIDS[i];
            if (teamMembershipRepository.findByPlayerId(playerId).isPresent()) {
                continue;
            }
            if (playerProfileRepository.findById(playerId).isEmpty()) {
                playerProfileRepository.save(PlayerProfile.builder().id(playerId).build());
            }
            Team team = teams.get(i % teams.size());
            teamMembershipRepository.save(TeamMembership.builder()
                    .playerId(playerId)
                    .teamId(team.getId())
                    .build());
        }
        log.debug("Assigned {} demo players across {} teams", DEMO_PLAYER_UUIDS.length, teams.size());
    }

    private void seedPlayerPlaythroughs(UUID playerId, String displayName, List<Scenario> scenarios, Random random) {
        // Each player plays 3-10 scenarios
        int playthroughCount = 3 + random.nextInt(8);

        for (int j = 0; j < playthroughCount; j++) {
            Scenario scenario = scenarios.get(random.nextInt(scenarios.size()));

            // Skip if already completed
            boolean alreadyCompleted = userProgressRepository
                    .findByUserIdAndScenarioIdAndStatus(playerId, scenario.getId(), ProgressStatus.COMPLETED)
                    .isPresent();
            if (alreadyCompleted) {
                j--;
                continue;
            }

            playScenario(playerId, scenario, random);
        }

        log.debug("Seeded {} playthroughs for {} ({})", playthroughCount, displayName, playerId);
    }

    private void playScenario(UUID playerId, Scenario scenario, Random random) {
        try {
            CarClass carClass = CAR_CLASSES[random.nextInt(CAR_CLASSES.length)];
            var startResponse = scenarioPlayService.start(scenario.getId(), playerId, carClass);
            var progressId = startResponse.progressId();

            // Play through the scenario with random choices
            ProgressStatus status = startResponse.status();
            while (status == ProgressStatus.IN_PROGRESS) {
                // Get current node to determine strategy
                var progress = scenarioPlayService.getProgress(progressId, playerId);
                if (progress.status() != ProgressStatus.IN_PROGRESS) {
                    break;
                }

                var currentNode = progress.currentNode();
                if (currentNode == null) {
                    break;
                }

                List<UUID> availableChoices = currentNode.choices().stream()
                        .map(ChoiceOptionResponse::id)
                        .toList();

                if (availableChoices.isEmpty()) {
                    break;
                }

                // Randomly select a strategy (prefer better choices 70% of the time)
                boolean preferBest = random.nextDouble() < 0.7;

                UUID selectedChoiceId;
                if (preferBest && !availableChoices.isEmpty()) {
                    // Use a simple heuristic: prefer first choice (usually best) more often
                    selectedChoiceId = availableChoices.get(random.nextInt(Math.max(1, availableChoices.size() / 2 + 1)));
                } else {
                    selectedChoiceId = availableChoices.get(random.nextInt(availableChoices.size()));
                }

                var choiceResponse = scenarioPlayService.choose(progressId, playerId, selectedChoiceId);
                status = choiceResponse.status();
            }
        } catch (Exception e) {
            log.debug("Error during demo playthrough for player {} in scenario {}", playerId, scenario.getCode(), e);
        }
    }
}
