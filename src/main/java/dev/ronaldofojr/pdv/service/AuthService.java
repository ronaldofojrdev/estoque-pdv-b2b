package dev.ronaldofojr.pdv.service;

import dev.ronaldofojr.pdv.config.AppProperties;
import dev.ronaldofojr.pdv.domain.AppUser;
import dev.ronaldofojr.pdv.repository.AppUserRepository;
import dev.ronaldofojr.pdv.service.Exceptions.InvalidCredentialsException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public record Token(String value, String username, String role, Instant expiresAt) {
    }

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties properties;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtEncoder jwtEncoder,
                       AppProperties properties) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public Token login(String username, String password) {
        AppUser user = users.findByUsername(username)
                .filter(AppUser::isActive)
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.jwt().expirationMinutes()));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", user.getRole().name())
                .build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Token(token, user.getUsername(), user.getRole().name(), expiresAt);
    }
}
