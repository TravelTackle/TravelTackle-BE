package Timeout.travel_tackle.auth;

import Timeout.travel_tackle.auth.dto.SignupRequest;
import Timeout.travel_tackle.auth.jwt.JwtProperties;
import Timeout.travel_tackle.auth.mail.VerificationMailSender;
import Timeout.travel_tackle.auth.service.EmailVerificationService;
import Timeout.travel_tackle.auth.service.SignupService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(JwtAuthenticationFlowTests.MailTestConfig.class)
class JwtAuthenticationFlowTests {

    @Autowired MockMvc mockMvc;
    @Autowired EmailVerificationService emailVerificationService;
    @Autowired SignupService signupService;
    @Autowired CapturingMailSender mailSender;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtProperties jwtProperties;

    @Test
    void loginMeRefreshAndLogoutUseHttpOnlyCookies() throws Exception {
        String email = "jwt-user@example.com";
        String password = "password123!";
        emailVerificationService.requestCode(email, "ko");
        emailVerificationService.confirmCode(email, mailSender.codeFor(email));
        signupService.signup(new SignupRequest(email, password, "JWT 사용자", "KR"));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jwt-user@example.com\",\"password\":\"password123!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie accessCookie = loginResult.getResponse().getCookie("access_token");
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertNotNull(accessCookie);
        assertNotNull(refreshCookie);
        assertTrue(accessCookie.isHttpOnly());
        assertTrue(refreshCookie.isHttpOnly());

        mockMvc.perform(get("/api/auth/me").cookie(accessCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessCookie.getValue()))
                .andExpect(status().isOk());

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie rotatedRefreshCookie = refreshResult.getResponse().getCookie("refresh_token");
        assertNotNull(rotatedRefreshCookie);
        assertNotEquals(refreshCookie.getValue(), rotatedRefreshCookie.getValue());

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout").cookie(rotatedRefreshCookie))
                .andExpect(status().isNoContent());
    }

    @Test
    void expiredAccessTokenCookieReturns401Json() throws Exception {
        Instant issuedAt = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(60))
                .subject(UUID.randomUUID().toString())
                .claim("token_type", "ACCESS")
                .build();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("access_token", expiredToken))
                        .header(HttpHeaders.ACCEPT, "application/json, text/plain, */*"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_013"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    @Test
    void missingTokenOnApiReturns401JsonEvenForHtmlAccept() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_013"));
    }

    @TestConfiguration
    static class MailTestConfig {
        @Bean
        @Primary
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }

    static class CapturingMailSender implements VerificationMailSender {
        private final Map<String, String> codes = new ConcurrentHashMap<>();

        @Override
        public void sendVerificationCode(String email, String code, String language) {
            codes.put(email, code);
        }

        @Override
        public void sendPasswordResetCode(String email, String code, String language) {
            codes.put(email, code);
        }

        String codeFor(String email) {
            return codes.get(email);
        }
    }
}
