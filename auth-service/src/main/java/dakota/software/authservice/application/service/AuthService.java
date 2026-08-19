package dakota.software.authservice.application.service;

import dakota.software.authservice.application.command.LoginUserCommand;
import dakota.software.authservice.application.command.RegisterUserCommand;
import dakota.software.authservice.application.exception.InvalidCredentialsException;
import dakota.software.authservice.application.exception.UserAlreadyExistsException;
import dakota.software.authservice.application.port.in.AuthUseCase;
import dakota.software.authservice.application.port.out.JwtPort;
import dakota.software.authservice.application.port.out.PasswordEncoderPort;
import dakota.software.authservice.application.port.out.UserRepositoryPort;
import dakota.software.authservice.domain.User;

import java.util.Optional;

/**
 * Application use-case service. Plain class wired as a bean in
 * {@code config/AppAuthConfig}; depends only on ports and commands.
 */
public class AuthService implements AuthUseCase {

    private static final String DEFAULT_ROLE = "USER";

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final JwtPort jwtPort;

    public AuthService(UserRepositoryPort userRepositoryPort,
                       PasswordEncoderPort passwordEncoderPort,
                       JwtPort jwtPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoderPort = passwordEncoderPort;
        this.jwtPort = jwtPort;
    }

    @Override
    public String register(RegisterUserCommand command) {
        if (userRepositoryPort.existsByUsername(command.username())) {
            throw new UserAlreadyExistsException("User already exists: " + command.username());
        }
        String encodedPassword = passwordEncoderPort.encode(command.password());
        User user = new User(null, command.username(), encodedPassword, DEFAULT_ROLE, command.email());
        userRepositoryPort.save(user);
        return jwtPort.generateToken(command.username(), DEFAULT_ROLE);
    }

    @Override
    public String login(LoginUserCommand command) {
        Optional<User> user = userRepositoryPort.findByUsername(command.username());
        if (user.isEmpty() || !passwordEncoderPort.matches(command.password(), user.get().password())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }
        return jwtPort.generateToken(user.get().username(), user.get().role());
    }
}