package dakota.software.authservice.config;

import dakota.software.authservice.application.port.in.AuthUseCase;
import dakota.software.authservice.application.port.out.JwtPort;
import dakota.software.authservice.application.port.out.PasswordEncoderPort;
import dakota.software.authservice.application.port.out.UserRepositoryPort;
import dakota.software.authservice.application.service.AuthService;
import dakota.software.authservice.infrastructure.persistence.UserJpaRepository;
import dakota.software.authservice.infrastructure.persistence.UserPersistenceAdapter;
import dakota.software.authservice.infrastructure.security.PasswordEncoderAdapter;
import dakota.software.authservice.infrastructure.token.NimbusJwtAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Explicit bean wiring for the hexagonal ports and the use-case service,
 * following the exemplar style (no @Service / @Repository annotations).
 */
@Configuration
public class AppAuthConfig {

    @Bean
    public UserRepositoryPort userRepositoryPort(UserJpaRepository userJpaRepository) {
        return new UserPersistenceAdapter(userJpaRepository);
    }

    @Bean
    public JwtPort jwtPort(@Value("${app.jwt-secret}") String jwtSecret,
                           @Value("${app.jwt-expiration-ms}") Long expirationMs) {
        return new NimbusJwtAdapter(jwtSecret, expirationMs);
    }

    @Bean
    public PasswordEncoderPort passwordEncoderPort(PasswordEncoder passwordEncoder) {
        return new PasswordEncoderAdapter(passwordEncoder);
    }

    @Bean
    public AuthUseCase authUseCase(UserRepositoryPort userRepositoryPort,
                                   PasswordEncoderPort passwordEncoderPort,
                                   JwtPort jwtPort) {
        return new AuthService(userRepositoryPort, passwordEncoderPort, jwtPort);
    }
}