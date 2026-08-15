package dakota.software.authservice.infrastructure.persistence;

import dakota.software.authservice.application.port.out.UserRepositoryPort;
import dakota.software.authservice.domain.User;

import java.util.Optional;

/**
 * Persistence adapter implementing the application port. Plain class wired
 * as a bean in {@code config/AppAuthConfig}; the Spring Data repository is
 * injected, this adapter owns the entity <-> domain conversion.
 */
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final UserJpaRepository userJpaRepository;

    public UserPersistenceAdapter(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userJpaRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userJpaRepository.existsByUsername(username);
    }

    @Override
    public User save(User user) {
        UserEntity entity = toEntity(user);
        return toDomain(userJpaRepository.save(entity));
    }

    private UserEntity toEntity(User user) {
        return new UserEntity(user.getUsername(), user.getPassword(), user.getRole(),user.getEmail());
    }

    private User toDomain(UserEntity entity) {
        return new User(entity.getId(), entity.getUsername(), entity.getPassword(), entity.getRole(),entity.getEmail());
    }
}