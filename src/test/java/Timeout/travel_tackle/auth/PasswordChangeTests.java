package Timeout.travel_tackle.auth;

import Timeout.travel_tackle.auth.dto.SignupRequest;
import Timeout.travel_tackle.auth.jwt.service.JwtService;
import Timeout.travel_tackle.auth.mail.VerificationMailSender;
import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.auth.service.EmailVerificationService;
import Timeout.travel_tackle.auth.service.SignupService;
import Timeout.travel_tackle.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(PasswordChangeTests.MailTestConfig.class)
class PasswordChangeTests {

    @Autowired MockMvc mockMvc;
    @Autowired EmailVerificationService emailVerificationService;
    @Autowired SignupService signupService;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;
    @Autowired CapturingVerificationMailSender mailSender;

    @Test
    void changesPasswordKeepsCurrentSessionButRevokesOtherSessions() throws Exception {
        String email = "change-pw@example.com";
        String oldPassword = "oldPassword1!";
        String newPassword = "newPassword1!";
        signUpLocalUser(email, oldPassword, "비번변경 사용자");

        // 세션 A: 비밀번호 변경을 요청할 세션
        MvcResult loginA = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, oldPassword)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie accessA = loginA.getResponse().getCookie("access_token");
        Cookie refreshA = loginA.getResponse().getCookie("refresh_token");
        assertNotNull(accessA);
        assertNotNull(refreshA);

        // 세션 B: 다른 기기 로그인 시뮬레이션
        MvcResult loginB = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, oldPassword)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refreshB = loginB.getResponse().getCookie("refresh_token");
        assertNotNull(refreshB);

        MvcResult changeResult = mockMvc.perform(patch("/api/auth/password")
                        .cookie(accessA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeJson(oldPassword, newPassword)))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie newRefreshA = changeResult.getResponse().getCookie("refresh_token");
        assertNotNull(newRefreshA);
        assertNotEquals(refreshA.getValue(), newRefreshA.getValue());

        // 다른 기기(B) 세션은 끊김
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshB))
                .andExpect(status().isUnauthorized());

        // 변경을 요청한 세션(A)은 새 refresh token으로 계속 사용 가능
        mockMvc.perform(post("/api/auth/refresh").cookie(newRefreshA))
                .andExpect(status().isNoContent());

        // 새 비밀번호로 로그인 성공, 예전 비밀번호는 실패
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, newPassword)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, oldPassword)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCurrentPasswordIsRejected() throws Exception {
        String email = "wrong-current@example.com";
        signUpLocalUser(email, "password123!", "오답 테스트");
        Cookie access = loginAndGetAccessCookie(email, "password123!");

        mockMvc.perform(patch("/api/auth/password")
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeJson("wrongPassword1!", "newPassword1!")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void socialOnlyAccountCannotChangePassword() throws Exception {
        User socialUser = userRepository.save(User.socialUser("social-change@example.com", "소셜 사용자"));
        String accessToken = jwtService.createAccessToken(socialUser);

        mockMvc.perform(patch("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeJson("anything", "newPassword1!")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameAsCurrentPasswordIsRejected() throws Exception {
        String email = "same-pw@example.com";
        String password = "password123!";
        signUpLocalUser(email, password, "동일비번 테스트");
        Cookie access = loginAndGetAccessCookie(email, password);

        mockMvc.perform(patch("/api/auth/password")
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeJson(password, password)))
                .andExpect(status().isBadRequest());
    }

    private Cookie loginAndGetAccessCookie(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("access_token");
    }

    private void signUpLocalUser(String email, String password, String name) {
        emailVerificationService.requestCode(email, "ko");
        emailVerificationService.confirmCode(email, mailSender.codeFor(email));
        signupService.signup(new SignupRequest(email, password, name, "KR"));
    }

    private String loginJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String changeJson(String currentPassword, String newPassword) {
        return "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(currentPassword, newPassword);
    }

    @TestConfiguration
    static class MailTestConfig {

        @Bean
        @Primary
        CapturingVerificationMailSender capturingVerificationMailSender() {
            return new CapturingVerificationMailSender();
        }
    }

    static class CapturingVerificationMailSender implements VerificationMailSender {

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
