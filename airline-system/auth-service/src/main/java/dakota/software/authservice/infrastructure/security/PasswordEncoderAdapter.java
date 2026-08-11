package dakota.software.authservice.infrastructure.security;

import dakota.software.authservice.application.port.out.PasswordEncoderPort;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Infrastructure adapter that adapts the Spring Security {@link PasswordEncoder}
 * (BCrypt) to the application port. Plain class wired in {@code config/AppAuthConfig}.
 */
public class PasswordEncoderAdapter implements PasswordEncoderPort {

    private final PasswordEncoder passwordEncoder;

    public PasswordEncoderAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String encode(String raw) {
        return passwordEncoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String encoded) {
        return passwordEncoder.matches(raw, encoded);
    }
}