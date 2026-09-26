package ru.vsm.backend.auth.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vsm.backend.auth.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByLogin(String login);

    boolean existsByEmail(String email);

    Optional<AppUser> findByLogin(String login);
}
