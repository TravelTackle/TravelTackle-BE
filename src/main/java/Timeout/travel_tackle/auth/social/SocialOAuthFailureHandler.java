package Timeout.travel_tackle.auth.social;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class SocialOAuthFailureHandler implements AuthenticationFailureHandler {

    private final String failureRedirectUrl;

    public SocialOAuthFailureHandler(
            @Value("${OAUTH_FAILURE_REDIRECT_URL:http://localhost:5173/login}")
            String failureRedirectUrl
    ) {
        this.failureRedirectUrl = failureRedirectUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        // 원인 코드(redirect_uri_mismatch, authorization_request_not_found, access_denied 등)를 로그와 프론트에 같이 넘긴다
        String reason = reasonOf(exception);
        log.warn("Social login failed: type={}, reason={}, message={}",
                exception.getClass().getSimpleName(), reason, exception.getMessage());
        String redirectUrl = UriComponentsBuilder.fromUriString(failureRedirectUrl)
                .queryParam("error", "social_login_failed")
                .queryParam("reason", reason)
                .build()
                .encode()
                .toUriString();
        response.sendRedirect(redirectUrl);
    }

    static String reasonOf(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth) {
            return oauth.getError().getErrorCode();
        }
        return exception.getClass().getSimpleName();
    }
}
