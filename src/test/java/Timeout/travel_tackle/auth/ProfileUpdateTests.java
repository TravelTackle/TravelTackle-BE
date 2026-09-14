package Timeout.travel_tackle.auth;

import Timeout.travel_tackle.auth.dto.SignupRequest;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ProfileUpdateTests.MailTestConfig.class)
class ProfileUpdateTests {

    @Autowired MockMvc mockMvc;
    @Autowired EmailVerificationService emailVerificationService;
    @Autowired SignupService signupService;
    @Autowired CapturingMailSender mailSender;

    private Cookie loginAndGetAccessCookie(String email) throws Exception {
        emailVerificationService.requestCode(email, "ko");
        emailVerificationService.confirmCode(email, mailSender.codeFor(email));
        signupService.signup(new SignupRequest(email, "password123!", "프로필유저", "KR"));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return loginResult.getResponse().getCookie("access_token");
    }

    @Test
    void meResponseIncludesLocalAuthProviderAndDefaultLanguage() throws Exception {
        Cookie accessCookie = loginAndGetAccessCookie("me-provider@example.com");

        mockMvc.perform(get("/api/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("ko"))
                .andExpect(jsonPath("$.authProviders[0]").value("LOCAL"))
                .andExpect(jsonPath("$.notifyEmail").value(true))
                .andExpect(jsonPath("$.notifyFeedback").value(true))
                .andExpect(jsonPath("$.notifyRecommend").value(true))
                .andExpect(jsonPath("$.notifyEvent").value(false));
    }

    @Test
    void notificationSettingsAreFullyReplaced() throws Exception {
        Cookie accessCookie = loginAndGetAccessCookie("notif@example.com");

        mockMvc.perform(put("/api/auth/notifications").cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notifyEmail\":false,\"notifyFeedback\":false,"
                                + "\"notifyRecommend\":true,\"notifyEvent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyEmail").value(false))
                .andExpect(jsonPath("$.notifyFeedback").value(false))
                .andExpect(jsonPath("$.notifyRecommend").value(true))
                .andExpect(jsonPath("$.notifyEvent").value(true));

        mockMvc.perform(get("/api/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyEmail").value(false))
                .andExpect(jsonPath("$.notifyEvent").value(true));
    }

    @Test
    void updatingOnlyNameLeavesLanguageUnchanged() throws Exception {
        Cookie accessCookie = loginAndGetAccessCookie("name-only@example.com");

        mockMvc.perform(patch("/api/auth/me").cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.preferredLanguage").value("ko"));
    }

    @Test
    void updatingOnlyLanguageLeavesNameUnchanged() throws Exception {
        Cookie accessCookie = loginAndGetAccessCookie("lang-only@example.com");

        mockMvc.perform(patch("/api/auth/me").cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredLanguage\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("프로필유저"))
                .andExpect(jsonPath("$.preferredLanguage").value("en"));
    }

    @Test
    void unsupportedLanguageCodeIsRejected() throws Exception {
        Cookie accessCookie = loginAndGetAccessCookie("lang-invalid@example.com");

        mockMvc.perform(patch("/api/auth/me").cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredLanguage\":\"xx\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
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
