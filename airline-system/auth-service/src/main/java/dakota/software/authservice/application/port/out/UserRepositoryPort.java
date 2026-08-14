package dakota.software.authservice.application.port.out;

import dakota.software.authservice.domain.User;

import java.util.Optional;

public interface UserRepositoryPort {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    User save(User user);
}