package ru.vsm.backend.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.auth.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByLogin(String login);

    boolean existsByEmail(String email);

    Optional<AppUser> findByLogin(String login);

    /** Поиск для {@code GET /api/admin/users?q=} — без учёта регистра, по трём полям сразу. */
    List<AppUser> findByLoginContainingIgnoreCaseOrEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String login, String email, String displayName);
}
