package ru.vsm.backend.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Подменяет заголовок {@code X-Player-Id} значением из проверенного JWT — так playerId из токена
 * доходит до существующих контроллеров ({@code ScenarioPlayController} и т.п.), которые читают
 * этот заголовок напрямую, без изменений в их коде: приоритет токена над заголовком, если
 * запрос прислал оба, и токен работает даже там, где клиент вовсе не прислал заголовок.
 */
class PlayerIdOverridingRequestWrapper extends HttpServletRequestWrapper {

    static final String PLAYER_ID_HEADER = "X-Player-Id";

    private final String playerId;

    PlayerIdOverridingRequestWrapper(HttpServletRequest request, String playerId) {
        super(request);
        this.playerId = playerId;
    }

    @Override
    public String getHeader(String name) {
        return PLAYER_ID_HEADER.equalsIgnoreCase(name) ? playerId : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return PLAYER_ID_HEADER.equalsIgnoreCase(name)
                ? Collections.enumeration(List.of(playerId))
                : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
        names.add(PLAYER_ID_HEADER);
        return Collections.enumeration(names);
    }
}
