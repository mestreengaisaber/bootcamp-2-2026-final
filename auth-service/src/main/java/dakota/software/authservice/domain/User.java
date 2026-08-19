package dakota.software.authservice.domain;

/**
 * Plain domain aggregate for an authenticated user. The domain itself has no
 * infrastructure dependencies: credentials are validated by the application
 * layer through ports, never by the entity.
 */
public record User(Long id, String username, String password, String role, String email) {
}