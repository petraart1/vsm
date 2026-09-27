package ru.vsm.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Дефолтная учётная запись администратора, создаваемая при старте, если ещё не существует. */
@ConfigurationProperties(prefix = "app.auth.admin")
@Getter
@Setter
public class AdminAccountProperties {

    private String login = "admin";

    private String password = "admin123";
}
