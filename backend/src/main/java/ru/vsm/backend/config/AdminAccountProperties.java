package ru.vsm.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Дефолтная учётная запись администратора, создаваемая при старте, если ещё не существует.
 *
 * <p>Bound from {@code app.auth.admin.login}/{@code app.auth.admin.password}. Значения по
 * умолчанию годятся только для демо-стенда — для боевого окружения сменить через переменные
 * окружения (см. README).
 */
@ConfigurationProperties(prefix = "app.auth.admin")
@Getter
@Setter
public class AdminAccountProperties {

    private String login = "admin";

    private String password = "admin123";
}
