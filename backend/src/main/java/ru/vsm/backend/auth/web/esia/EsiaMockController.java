package ru.vsm.backend.auth.web.esia;

import jakarta.validation.Valid;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.auth.esia.service.EsiaMockService;
import ru.vsm.backend.auth.web.dto.LoginResponse;
import ru.vsm.backend.auth.web.esia.dto.EsiaCallbackRequest;

/**
 * Демо-заглушка входа через Госуслуги/ЕСИА (см. javadoc {@link EsiaMockService} — почему заглушка,
 * а не настоящая интеграция). Гейтится свойством {@code app.esia.mock.enabled} (по умолчанию
 * {@code true}) — при {@code false} бин контроллера не создаётся, все три пути отвечают 404, как
 * любой незамапленный путь.
 *
 * <p>Поток намеренно повторяет форму OAuth2 authorization code (без реального протокола ЕСИА):
 * {@code GET /authorize} — HTML-страница выбора тестового гражданина вместо формы логина Госуслуг;
 * выбор -> редирект на {@code redirect_uri} с {@code ?code=...}; {@code POST /callback} обменивает
 * код на наш JWT (тем же форматом ответа, что {@code POST /api/auth/login}).
 */
@RestController
@RequestMapping("/api/auth/esia")
@ConditionalOnProperty(prefix = "app.esia.mock", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class EsiaMockController {

    private final EsiaMockService esiaMockService;

    @GetMapping(value = "/authorize", produces = MediaType.TEXT_HTML_VALUE)
    public String authorize(@RequestParam("redirect_uri") String redirectUri) {
        return esiaMockService.renderCitizenSelectionPage(redirectUri);
    }

    @GetMapping("/select")
    public ResponseEntity<Void> select(
            @RequestParam("redirect_uri") String redirectUri, @RequestParam("citizen") String citizenCode) {
        String code = esiaMockService.issueAuthorizationCode(citizenCode);
        String separator = redirectUri.contains("?") ? "&" : "?";
        String encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUri + separator + "code=" + encodedCode))
                .build();
    }

    @PostMapping("/callback")
    public LoginResponse callback(@Valid @RequestBody EsiaCallbackRequest request) {
        return esiaMockService.exchangeCode(request.code());
    }
}
