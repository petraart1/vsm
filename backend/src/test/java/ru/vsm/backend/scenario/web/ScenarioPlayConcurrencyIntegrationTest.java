package ru.vsm.backend.scenario.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;
import ru.vsm.backend.scenario.repository.ScenarioChoiceHistoryRepository;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Регрессионный тест на находку code-review HIGH: гонка двойного клика/повторного запроса на
 * {@code POST /api/scenarios/progress/{id}/choices/{choiceId}}. Два параллельных запроса с
 * одинаковым {@code choiceId} на одно и то же прохождение раньше оба успевали прочитать
 * {@code UserProgress} до того, как первый коммитил изменения, и оба коммитили: дублирующая
 * строка в {@code scenario_choice_history} с одинаковым {@code sequence_index}, а на терминальном
 * выборе — {@link ScenarioCompletedEvent} публиковался дважды.
 *
 * <p>Фикс — пессимистичная блокировка {@code UserProgress} (см.
 * {@code UserProgressRepository.findByIdForUpdate}), которая сериализует конкурентные запросы:
 * второй запрос ждёт коммита первого, видит уже {@code COMPLETED}-статус и получает {@code 409},
 * а не повторяет запись.
 *
 * <p>Подсчёт публикаций {@link ScenarioCompletedEvent} — через собственный синхронный
 * {@link ApplicationListener}, а не {@code ApplicationEvents} (её привязка к потоку теста не
 * гарантированно ловит события, опубликованные из пула потоков экзекьютора).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(ScenarioPlayConcurrencyIntegrationTest.EventCounterConfig.class)
class ScenarioPlayConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ScenarioChoiceHistoryRepository scenarioChoiceHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AtomicInteger scenarioCompletedEventCounter;

    @Test
    void concurrentChooseOfSameTerminalChoiceCommitsExactlyOnce() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("medical-passenger-unwell").orElseThrow().getId();
        UUID playerId = UUID.randomUUID();

        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                .andReturn().getResponse().getContentAsString();
        JsonNode startBody = objectMapper.readTree(startResponse);
        UUID progressId = UUID.fromString(startBody.get("progressId").asText());
        // Терминальный выбор в один шаг от старта (start -> failure-medication) — проще всего
        // воспроизвести двойную публикацию ScenarioCompletedEvent из находки code-review.
        UUID giveMedicationChoiceId =
                findChoiceId(startBody.get("currentNode").get("choices"), "give-own-medication");

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Integer> statuses;
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mockMvc.perform(post(
                                    "/api/scenarios/progress/{progressId}/choices/{choiceId}",
                                    progressId, giveMedicationChoiceId)
                                    .header(ScenarioPlayController.PLAYER_ID_HEADER, playerId.toString()))
                            .andReturn().getResponse().getStatus();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();

            statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(20, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        // Ровно один запрос выигрывает блокировку и завершает прохождение (200), второй видит
        // уже COMPLETED-статус после разблокировки и получает конфликт (409), а не 500 и не 200.
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        assertThat(scenarioChoiceHistoryRepository.findByUserProgressIdOrderBySequenceIndex(progressId))
                .hasSize(1);
        assertThat(scenarioCompletedEventCounter.get()).isEqualTo(1);
    }

    private UUID findChoiceId(JsonNode choices, String code) {
        for (JsonNode choice : choices) {
            if (code.equals(choice.get("code").asText())) {
                return UUID.fromString(choice.get("id").asText());
            }
        }
        throw new IllegalStateException("Выбор '" + code + "' не найден среди " + choices);
    }

    @TestConfiguration
    static class EventCounterConfig {

        @Bean
        AtomicInteger scenarioCompletedEventCounter() {
            return new AtomicInteger();
        }

        @Bean
        ScenarioCompletedEventCountingListener scenarioCompletedEventCountingListener(
                AtomicInteger scenarioCompletedEventCounter) {
            return new ScenarioCompletedEventCountingListener(scenarioCompletedEventCounter);
        }

        /**
         * Plain {@code @EventListener} bean, не {@link org.springframework.context.ApplicationListener}:
         * {@link ScenarioCompletedEvent} — обычный record, не наследует {@code ApplicationEvent}
         * (осознанное решение scenario-engine, см. Javadoc события).
         */
        static class ScenarioCompletedEventCountingListener {

            private final AtomicInteger counter;

            ScenarioCompletedEventCountingListener(AtomicInteger counter) {
                this.counter = counter;
            }

            @EventListener
            void onScenarioCompleted(ScenarioCompletedEvent event) {
                counter.incrementAndGet();
            }
        }
    }
}
