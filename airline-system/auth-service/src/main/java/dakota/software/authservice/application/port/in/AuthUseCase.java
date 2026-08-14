package dakota.software.authservice.application.port.in;

import dakota.software.authservice.application.command.LoginUserCommand;
import dakota.software.authservice.application.command.RegisterUserCommand;

public interface AuthUseCase {

    String register(RegisterUserCommand command);
    String login(LoginUserCommand command);
}