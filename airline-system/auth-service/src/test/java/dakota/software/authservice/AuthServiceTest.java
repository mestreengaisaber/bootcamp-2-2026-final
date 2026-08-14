package dakota.software.authservice;

import dakota.software.authservice.application.command.LoginUserCommand;
import dakota.software.authservice.application.command.RegisterUserCommand;
import dakota.software.authservice.application.exception.InvalidCredentialsException;
import dakota.software.authservice.application.exception.UserAlreadyExistsException;
import dakota.software.authservice.application.port.out.JwtPort;
import dakota.software.authservice.application.port.out.PasswordEncoderPort;
import dakota.software.authservice.application.port.out.UserRepositoryPort;
import dakota.software.authservice.application.service.AuthService;
import dakota.software.authservice.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;
    @Mock
    private PasswordEncoderPort passwordEncoderPort;
    @Mock
    private JwtPort jwtPort;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepositoryPort, passwordEncoderPort, jwtPort);
    }

    @Test
    void register_returnsTokenAndSavesEncodedPassword() {
        when(userRepositoryPort.existsByUsername("ana")).thenReturn(false);
        when(passwordEncoderPort.encode("1234")).thenReturn("encoded-password");
        when(jwtPort.generateToken("ana", "USER")).thenReturn("jwt-token");

        String token = authService.register(new RegisterUserCommand("ana", "1234", "ana@test.com"));

        assertEquals("jwt-token", token);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepositoryPort).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("ana", saved.getUsername());
        assertEquals("encoded-password", saved.getPassword());
        assertEquals("USER", saved.getRole());
        assertEquals("ana@test.com", saved.getEmail());
    }

    @Test
    void register_duplicateUsername_throwsUserAlreadyExists() {
        when(userRepositoryPort.existsByUsername("ana")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class,
                () -> authService.register(new RegisterUserCommand("ana", "1234", "ana@test.com")));
    }

    @Test
    void login_validCredentials_returnsToken() {
        when(userRepositoryPort.findByUsername("ana"))
                .thenReturn(Optional.of(new User(1L, "ana", "encoded-password", "USER",null)));
        when(passwordEncoderPort.matches("1234", "encoded-password")).thenReturn(true);
        when(jwtPort.generateToken("ana", "USER")).thenReturn("jwt-token");

        String token = authService.login(new LoginUserCommand("ana", "1234"));

        assertEquals("jwt-token", token);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        when(userRepositoryPort.findByUsername("ana"))
                .thenReturn(Optional.of(new User(1L, "ana", "encoded-password", "USER",null)));
        when(passwordEncoderPort.matches("wrong", "encoded-password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginUserCommand("ana", "wrong")));
    }

    @Test
    void login_unknownUser_throwsInvalidCredentials() {
        when(userRepositoryPort.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginUserCommand("nobody", "1234")));
    }
}