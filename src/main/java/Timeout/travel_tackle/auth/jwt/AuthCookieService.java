package Timeout.travel_tackle.auth.jwt;

import Timeout.travel_tackle.auth.jwt.service.RefreshTokenService.AuthTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AuthCookieService implements BearerTokenResolver {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private final JwtProperties properties;
    private final DefaultBearerTokenResolver headerTokenResolver = new DefaultBearerTokenResolver();

    public void writeTokens(HttpServletResponse response, AuthTokens tokens) {
        addCookie(response, ACCESS_TOKEN_COOKIE,
                tokens.accessToken(), properties.accessTokenTtl(), "/");
        addCookie(response, REFRESH_TOKEN_COOKIE,
                tokens.refreshToken(), properties.refreshTokenTtl(), "/api/auth");
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String cookieToken = readCookie(request, ACCESS_TOKEN_COOKIE);
        return cookieToken != null ? cookieToken : headerTokenResolver.resolve(request);
    }

    public String readRefreshToken(HttpServletRequest request) { //refresh 토큰 검증
        return readCookie(request, REFRESH_TOKEN_COOKIE);
    }

    public void clearTokens(HttpServletResponse response) { //토큰 삭제
        addCookie(response, ACCESS_TOKEN_COOKIE, "", Duration.ZERO, "/");
        addCookie(response, REFRESH_TOKEN_COOKIE, "", Duration.ZERO, "/api/auth");
    }

    private String readCookie(HttpServletRequest request, String name) { //쿠키 파싱
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void addCookie(HttpServletResponse response, String name, String value,
                           Duration maxAge, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true) //js에서 토큰 못 읽게 방지
                .secure(properties.secureCookie()) //https에서만 쿠키 전송
                .sameSite("Lax") //CSRF 위험을 줄임.
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
