package ru.vsm.backend.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.vsm.backend.auth.domain.AppUser;
import ru.vsm.backend.auth.domain.UserRole;
import ru.vsm.backend.auth.repository.AppUserRepository;
import ru.vsm.backend.auth.security.PlayerAccessGuard;
import ru.vsm.backend.auth.web.dto.AdminUserPatchRequest;
import ru.vsm.backend.auth.web.dto.UserProfileResponse;

/**
 * Административная консоль: список учётных записей и правка роли/подтверждения/имени. Путь под
 * {@code /api/admin/**}, доступен только роли {@code ADMIN} (см. {@code SecurityConfig}).
 *
 * <p>Снять роль {@code ADMIN} с собственной учётной записи запрещено — иначе администратор мог бы
 * случайно заблокировать себе доступ к этой же консоли без возможности вернуть права.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AppUserRepository appUserRepository;
    private final PlayerAccessGuard playerAccessGuard;

    @GetMapping
    @Transactional(readOnly = true)
    public List<UserProfileResponse> list(@RequestParam(required = false) String q) {
        String query = q == null ? "" : q.trim();
        List<AppUser> users = query.isEmpty()
                ? appUserRepository.findAll()
                : appUserRepository
                        .findByLoginContainingIgnoreCaseOrEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                                query, query, query);
        return users.stream()
                .sorted(Comparator.comparing(AppUser::getCreatedAt))
                .map(UserProfileResponse::from)
                .toList();
    }

    @PatchMapping("/{id}")
    @Transactional
    public UserProfileResponse patch(
            @PathVariable UUID id, @RequestBody AdminUserPatchRequest request, HttpServletRequest httpRequest) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Учётная запись не найдена"));

        if (request.role() != null && request.role() != user.getRole()) {
            if (user.getRole() == UserRole.ADMIN && request.role() != UserRole.ADMIN && isSelf(httpRequest, id)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Нельзя снять права администратора с собственной учётной записи");
            }
            user.setRole(request.role());
        }
        if (request.verified() != null) {
            user.setVerified(request.verified());
        }
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }

        return UserProfileResponse.from(appUserRepository.save(user));
    }

    private boolean isSelf(HttpServletRequest request, UUID targetId) {
        Optional<UUID> requester = playerAccessGuard.currentPlayerId(request);
        return requester.isPresent() && requester.get().equals(targetId);
    }
}
