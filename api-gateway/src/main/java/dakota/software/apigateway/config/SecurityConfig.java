package dakota.software.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.util.ArrayList;
import java.util.Collection;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/v1/flights/**").hasAnyRole("PASSENGER", "AGENT", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings").hasAnyRole("PASSENGER", "AGENT", "ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/v1/bookings").hasAnyRole("AGENT", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings/**").hasAnyRole("PASSENGER", "AGENT", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/checkin/**").hasAnyRole("PASSENGER", "AGENT", "ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.jwt-secret}") final String jwtSecret
    ) {
        SecretKey secretKey = new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }


    /*
    JWT
 │
 ├── NimbusJwtDecoder
 │       └── valida firma, issuer, etc.
 │
 └── JwtAuthenticationConverter
         └── convierte el JWT en Authentication
                 └── authorities / roles
     */

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Object roles = jwt.getClaim("roles");
            if (roles instanceof String role) {
                String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                authorities.add(new SimpleGrantedAuthority(authority));
            }
            return authorities;
        };
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    RouterFunction<ServerResponse> routes() {
        return GatewayRouterFunctions.route("bookings")
                .route(RequestPredicates.path("/api/v1/bookings/**"),
                        HandlerFunctions.http())
                // http() without a URI resolves the target from the
                // GATEWAY_REQUEST_URL_ATTR request attribute, which is only
                // populated by the uri() before-filter. Without it, routing
                // fails at runtime with 500.
                .before(BeforeFilterFunctions.uri("http://localhost:8082"))
                .before(request -> {

                    JwtAuthenticationToken auth =
                            (JwtAuthenticationToken) SecurityContextHolder
                                    .getContext()
                                    .getAuthentication();

                    String userId = auth.getToken().getSubject();

                    return ServerRequest.from(request)
                            .header("X-User-Id", userId)
                            .build();
                })
                .build();
    }

  
}
