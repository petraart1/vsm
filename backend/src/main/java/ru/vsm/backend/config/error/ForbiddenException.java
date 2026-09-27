package ru.vsm.backend.config.error;

/** отдельный код {@code forbidden} (не переиспользует {@code access_denied}, который уже занят */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
