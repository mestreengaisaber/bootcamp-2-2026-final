package dakota.software.authservice.application.command;

public record RegisterUserCommand(String username, String password, String email) {
}