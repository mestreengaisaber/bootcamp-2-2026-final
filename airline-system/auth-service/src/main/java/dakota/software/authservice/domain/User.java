package dakota.software.authservice.domain;

/**
 * Plain domain aggregate for an authenticated user. The domain itself has no
 * infrastructure dependencies: credentials are validated by the application
 * layer through ports, never by the entity.
 */
public class User {

    private final Long id;
    private final String username;
    private final String password;
    private final String role;
    private final String email;

    public User(Long id, String username, String password, String role, String email) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }
}