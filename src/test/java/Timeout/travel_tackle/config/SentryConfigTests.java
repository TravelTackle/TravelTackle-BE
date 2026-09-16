package Timeout.travel_tackle.config;

import io.sentry.protocol.User;
import io.sentry.spring7.SentryUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SentryConfigTests {

    private final SentryUserProvider provider = new SentryConfig().sentryUserProvider();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void attachesOnlyJwtSubjectAsSentryUser() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", "11111111-1111-1111-1111-111111111111", "email", "secret@example.com"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        User user = provider.provideUser();

        assertEquals("11111111-1111-1111-1111-111111111111", user.getId());
        assertNull(user.getEmail());
        assertNull(user.getUsername());
    }

    @Test
    void anonymousRequestHasNoSentryUser() {
        assertNull(provider.provideUser());
    }
}
