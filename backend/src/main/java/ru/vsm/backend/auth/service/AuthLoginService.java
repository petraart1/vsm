package ru.vsm.backend.auth.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.vsm.backend.auth.domain.AppUser;
import ru.vsm.backend.auth.repository.AppUserRepository;
import ru.vsm.backend.auth.security.JwtClaims;
import ru.vsm.backend.auth.security.JwtService;
import ru.vsm.backend.auth.service.exception.InvalidCredentialsException;
import ru.vsm.backend.auth.service.exception.InvalidTokenException;
import ru.vsm.backend.auth.web.dto.LoginRequest;
import ru.vsm.backend.auth.web.dto.LoginResponse;
import ru.vsm.backend.auth.web.dto.UserProfileResponse;

/** Логин по login+password (JWT) и разбор текущего токена для {@code GET /api/auth/me}. */
@Service
@RequiredArgsConstructor
public class AuthLoginService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        String login = request.login() == null ? null : request.login().trim();
        AppUser user = Optional.ofNullable(login)
                .flatMap(appUserRepository::findByLogin)
                .filter(u -> request.password() != null && passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("invalid_credentials"));
        String token = jwtService.issueToken(user);
        return new LoginResponse(token, UserProfileResponse.from(user));
    }

    public UserProfileResponse me(String authorizationHeader) {
        UUID playerId = JwtService.extractBearerToken(authorizationHeader)
                .flatMap(jwtService::parse)
                .map(JwtClaims::playerId)
                .orElseThrow(() -> new InvalidTokenException("invalid_token"));
        AppUser user = appUserRepository.findById(playerId)
                .orElseThrow(() -> new InvalidTokenException("invalid_token"));
        return UserProfileResponse.from(user);
    }
}
