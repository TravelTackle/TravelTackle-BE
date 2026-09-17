package Timeout.travel_tackle.auth.social;
import Timeout.travel_tackle.auth.social.service.SocialLoginService;

import Timeout.travel_tackle.auth.jwt.AuthCookieService;
import Timeout.travel_tackle.auth.jwt.service.RefreshTokenService;
import Timeout.travel_tackle.auth.jwt.service.RefreshTokenService.AuthTokens;
import Timeout.travel_tackle.entity.Enum.AuthProvider;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SocialOAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final SocialLoginService socialLoginService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService authCookieService;
    private final String successRedirectUrl;
    private final String failureRedirectUrl;

    public SocialOAuthSuccessHandler(
            SocialLoginService socialLoginService,
            RefreshTokenService refreshTokenService,
            AuthCookieService authCookieService,
            @Value("${OAUTH_SUCCESS_REDIRECT_URL:http://localhost:5173/oauth/callback}")
            String successRedirectUrl,
            @Value("${OAUTH_FAILURE_REDIRECT_URL:http://localhost:5173/login}")
            String failureRedirectUrl
    ) {
        this.socialLoginService = socialLoginService;
        this.refreshTokenService = refreshTokenService;
        this.authCookieService = authCookieService;
        this.successRedirectUrl = successRedirectUrl;
        this.failureRedirectUrl = failureRedirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
        }

        try {
            AuthProvider provider = parseProvider(oauthToken.getAuthorizedClientRegistrationId());
            User user = socialLoginService.login(provider, oauthToken.getPrincipal().getAttributes());
            AuthTokens tokens = refreshTokenService.issueTokens(user);
            authCookieService.writeTokens(response, tokens);
            response.sendRedirect(redirect(successRedirectUrl, "login", "success"));
        } catch (CustomException exception) {
            log.warn("Social account processing failed: code={}", exception.getErrorCode().getCode());
            authCookieService.clearTokens(response);
            response.sendRedirect(redirect(failureRedirectUrl, "error", exception.getErrorCode().getCode()));
        }
    }

    private AuthProvider parseProvider(String registrationId) {
        try {
            return AuthProvider.valueOf(registrationId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.UNSUPPORTED_AUTH_PROVIDER);
        }
    }

    private String redirect(String baseUrl, String parameter, String value) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam(parameter, value)
                .build()
                .encode()
                .toUriString();
    }
}
