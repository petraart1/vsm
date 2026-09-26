package ru.vsm.backend.scenario.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Загружает сценарии при старте приложения из {@code classpath:scenarios/*.json} (после того как
 * Liquibase применил миграции — {@code ApplicationRunner}'ы выполняются после инициализации
 * контекста, куда входит и {@code spring-boot-starter-liquibase}).
 *
 * <p>Формат файла и правила идемпотентности — см. {@link ScenarioSeedDto} и
 * {@link ScenarioSeedService}.
 *
 * <p>Использует собственный {@link ObjectMapper} (не Spring-бин): в Spring Boot 4.1 основной
 * автоконфигурируемый JSON-маппер — Jackson 3 ({@code tools.jackson.databind.ObjectMapper}),
 * а классический Jackson 2 ({@code com.fasterxml.jackson.databind}, который используем здесь —
 * он проще и не завязан на web-конфигурацию) присутствует на classpath транзитивно, но бином
 * Spring не публикуется. Для одноразового парсинга seed-файлов при старте это не критично.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ScenarioSeedLoader implements ApplicationRunner {

    private static final String LOCATION_PATTERN = "classpath:scenarios/*.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScenarioSeedService scenarioSeedService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(LOCATION_PATTERN);
        if (resources.length == 0) {
            log.warn("Нет seed-файлов сценариев по пути {}", LOCATION_PATTERN);
            return;
        }

        java.util.Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
        int loaded = 0;
        for (Resource resource : resources) {
            try (var in = resource.getInputStream()) {
                ScenarioSeedDto dto = objectMapper.readValue(in, ScenarioSeedDto.class);
                scenarioSeedService.seed(dto);
                loaded++;
            } catch (Exception e) {
                log.error("Не удалось загрузить seed-файл {}: {}", resource.getFilename(), e.getMessage(), e);
                throw e;
            }
        }
        log.info("Seed сценариев: обработано {} файл(ов) из {}", loaded, LOCATION_PATTERN);
    }
}
