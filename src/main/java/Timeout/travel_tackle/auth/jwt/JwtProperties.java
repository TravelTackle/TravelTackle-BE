package Timeout.travel_tackle.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

public record JwtProperties(
        String secret,
        Duration accessTokenTtl, //만료시간
        Duration refreshTokenTtl, //만료 시간
        boolean secureCookie,
        String issuer
) {
    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");
        }
    }
}
