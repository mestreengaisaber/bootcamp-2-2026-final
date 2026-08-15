package dakota.software.authservice.application.port.out;

public interface JwtPort {

    String generateToken(String username, String role);
}