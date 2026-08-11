package dakota.software.authservice.infrastructure.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dakota.software.authservice.application.port.out.JwtPort;

import java.util.Date;

/**
 * Adapter that issues HS256-signed JWTs with the Nimbus JOSE library, mirroring
 * the old layered {@code NimbusJWTServiceImpl}. Payload claims: sub (username),
 * issuer, iat, exp and a "roles" claim. Plain class wired in {@code config/AppAuthConfig}.
 */
public class NimbusJwtAdapter implements JwtPort {

    public static final String ISSUER = "https://api.airline-system.dev";

    private final String jwtSecret;
    private final Long expirationMs;

    public NimbusJwtAdapter(String jwtSecret, Long expirationMs) {
        this.jwtSecret = jwtSecret;
        this.expirationMs = expirationMs;
    }

    @Override
    public String generateToken(String username, String role) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(username)
                    .issuer(ISSUER)
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + expirationMs))
                    .claim("roles", role)
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJWT.sign(new MACSigner(jwtSecret));
            return signedJWT.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to generate JWT token", ex);
        }
    }
}