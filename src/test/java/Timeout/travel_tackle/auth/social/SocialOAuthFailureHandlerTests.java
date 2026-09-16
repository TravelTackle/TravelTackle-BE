package Timeout.travel_tackle.auth.social;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialOAuthFailureHandlerTests {

    @Test
    void redirectCarriesOAuthErrorCodeAsReason() throws Exception {
        SocialOAuthFailureHandler handler = new SocialOAuthFailureHandler("http://localhost:5173/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("redirect_uri_mismatch", "bad redirect", null)));

        assertEquals("http://localhost:5173/login?error=social_login_failed&reason=redirect_uri_mismatch",
                response.getRedirectedUrl());
    }

    @Test
    void nonOAuthFailureUsesExceptionTypeAsReason() {
        assertEquals("BadCredentialsException", SocialOAuthFailureHandler.reasonOf(new BadCredentialsException("x")));
    }
}
