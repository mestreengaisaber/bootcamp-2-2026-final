package dakota.software.authservice.infrastructure.web;

import dakota.software.authservice.application.command.LoginUserCommand;
import dakota.software.authservice.application.command.RegisterUserCommand;
import dakota.software.authservice.application.port.in.AuthUseCase;
import dakota.software.authservice.infrastructure.web.dto.LoginUserRequest;
import dakota.software.authservice.infrastructure.web.dto.RegisterUserRequest;
import dakota.software.authservice.infrastructure.web.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody RegisterUserRequest request) {
        String token = authUseCase.register(new RegisterUserCommand(request.username(), request.password(), request.email()));
        return new TokenResponse(token);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginUserRequest request) {
        String token = authUseCase.login(new LoginUserCommand(request.username(), request.password()));
        return new TokenResponse(token);
    }
}