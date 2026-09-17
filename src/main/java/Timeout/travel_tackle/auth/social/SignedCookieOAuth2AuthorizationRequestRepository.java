package Timeout.travel_tackle.auth.social;

import Timeout.travel_tackle.auth.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Component
public class SignedCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_authorization_request";
    private static final Duration COOKIE_TTL = Duration.ofMinutes(3);
    private final byte[] signingKey;
    private final boolean secureCookie;

    public SignedCookieOAuth2AuthorizationRequestRepository(JwtProperties properties) {
        this.signingKey = properties.secret().getBytes(StandardCharsets.UTF_8);
        this.secureCookie = properties.secureCookie();
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String cookieValue = readCookie(request);
        if (cookieValue == null) {
            return null;
        }
        try {
            String[] parts = cookieValue.split("\\.", 2);
            if (parts.length != 2) {
                return null;
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            byte[] providedSignature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(sign(payload), providedSignature)) {
                return null;
            }
            return deserialize(payload);
        } catch (RuntimeException | IOException | ClassNotFoundException exception) {
            log.warn("Invalid OAuth authorization request cookie: {}", exception.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            deleteCookie(response);
            return;
        }
        try {
            byte[] payload = serialize(authorizationRequest);
            String value = Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
                    + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
            writeCookie(response, value, COOKIE_TTL);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store OAuth authorization request", exception);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        deleteCookie(response);
        return authorizationRequest;
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private byte[] serialize(OAuth2AuthorizationRequest request) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(request);
            return bytes.toByteArray();
        }
    }

    private OAuth2AuthorizationRequest deserialize(byte[] payload)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(payload))) {
            input.setObjectInputFilter(info -> {
                if (info.depth() > 20 || info.references() > 1_000) {
                    return ObjectInputFilter.Status.REJECTED;
                }
                Class<?> serialClass = info.serialClass();
                if (serialClass == null || serialClass.isArray()) {
                    return ObjectInputFilter.Status.UNDECIDED;
                }
                String name = serialClass.getName();
                return name.startsWith("java.") || name.startsWith("org.springframework.security.oauth2.")
                        ? ObjectInputFilter.Status.ALLOWED
                        : ObjectInputFilter.Status.REJECTED;
            });
            return (OAuth2AuthorizationRequest) input.readObject();
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign OAuth authorization request", exception);
        }
    }

    private void deleteCookie(HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

    private void writeCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
