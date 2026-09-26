package ru.vsm.backend.gamification.team.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.gamification.team.domain.Team;
import ru.vsm.backend.gamification.team.repository.TeamRepository;

/**
 * Заполняет каталог команд (бригад/депо) при каждом старте приложения. Идемпотентно —
 * пропускает уже существующие по {@link Team#getCode()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(100)
public class TeamSeeder implements ApplicationRunner {

    private final TeamRepository teamRepository;

    private record Template(String code, String name, String depot) {
    }

    private static final List<Template> TEMPLATES = List.of(
            new Template("brigade-1-msk", "Бригада №1", "Москва Ленинградская"),
            new Template("brigade-2-spb", "Бригада №2", "Санкт-Петербург Московский"),
            new Template("brigade-3-msk", "Бригада №3", "Москва Ленинградская"),
            new Template("brigade-4-spb", "Бригада №4", "Санкт-Петербург Московский"),
            new Template("brigade-5-msk", "Бригада №5", "Москва Ленинградская"));

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Template t : TEMPLATES) {
            if (teamRepository.findByCode(t.code()).isPresent()) {
                continue;
            }
            teamRepository.save(Team.builder()
                    .code(t.code())
                    .name(t.name())
                    .depot(t.depot())
                    .build());
            log.info("Засеяна команда: {}", t.code());
        }
    }
}
