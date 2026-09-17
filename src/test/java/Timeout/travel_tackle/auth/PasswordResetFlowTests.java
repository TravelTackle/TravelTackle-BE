package Timeout.travel_tackle.auth;

import Timeout.travel_tackle.auth.dto.SignupRequest;
import Timeout.travel_tackle.auth.mail.VerificationMailSender;
import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.auth.service.EmailVerificationService;
import Timeout.travel_tackle.auth.service.PasswordResetService;
import Timeout.travel_tackle.auth.service.SignupService;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(PasswordResetFlowTests.MailTestConfig.class)
class PasswordResetFlowTests {

    @Autowired MockMvc mockMvc;
    @Autowired EmailVerificationService emailVerificationService;
    @Autowired SignupService signupService;
    @Autowired PasswordResetService passwordResetService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CapturingVerificationMailSender mailSender;

    @Test
    void requestAndConfirmResetsPasswordAndRevokesExistingSessions() throws Exception {
        String email = "reset-user@example.com";
        String oldPassword = "oldPassword1!";
        String newPassword = "newPassword1!";
        signUpLocalUser(email, oldPassword, "재설정 사용자");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, oldPassword)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie oldRefreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertNotNull(oldRefreshCookie);

        passwordResetService.requestReset(email);
        String code = mailSender.codeFor(email);
        assertNotNull(code);

        passwordResetService.confirmReset(email, code, newPassword);

        User user = userRepository.findByEmail(email).orElseThrow();
        assertTrue(passwordEncoder.matches(newPassword, user.getPasswordHash()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, oldPassword)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, newPassword)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(oldRefreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestResetForNonexistentOrSocialOnlyEmailDoesNotSendMail() {
        String nonexistentEmail = "nobody@example.com";
        passwordResetService.requestReset(nonexistentEmail);
        assertNull(mailSender.codeFor(nonexistentEmail));

        String socialOnlyEmail = "social-only@example.com";
        userRepository.save(User.socialUser(socialOnlyEmail, "소셜 사용자"));
        passwordResetService.requestReset(socialOnlyEmail);
        assertNull(mailSender.codeFor(socialOnlyEmail));
    }

    // incrementAttempts()는 REQUIRES_NEW로 별도 트랜잭션에 즉시 커밋되어야 하는데,
    // 클래스 레벨 @Transactional 안에서는 앞서 저장한 PasswordReset row가 아직 커밋되지 않아
    // 별도 트랜잭션에서 보이지 않는다(다른 커넥션이라 uncommitted insert를 못 봄).
    // 그래서 이 테스트만 실제 커밋이 일어나도록 테스트 트랜잭션을 껐다.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void wrongCodeAttemptsLockAfterFiveFailures() {
        String email = "lockout-user@example.com";
        signUpLocalUser(email, "password123!", "잠금 테스트");

        passwordResetService.requestReset(email);
        String correctCode = mailSender.codeFor(email);
        assertNotNull(correctCode);
        String wrongCode = "000000".equals(correctCode) ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            CustomException exception = assertThrows(CustomException.class,
                    () -> passwordResetService.confirmReset(email, wrongCode, "newPassword1!"));
            assertEquals(ErrorCode.INVALID_OR_EXPIRED_PASSWORD_RESET_CODE, exception.getErrorCode());
        }

        CustomException locked = assertThrows(CustomException.class,
                () -> passwordResetService.confirmReset(email, correctCode, "newPassword1!"));
        assertEquals(ErrorCode.PASSWORD_RESET_LOCKED, locked.getErrorCode());
    }

    @Test
    void reusedCodeIsRejected() {
        String email = "reuse-user@example.com";
        signUpLocalUser(email, "password123!", "재사용 테스트");

        passwordResetService.requestReset(email);
        String code = mailSender.codeFor(email);

        passwordResetService.confirmReset(email, code, "newPassword1!");

        CustomException exception = assertThrows(CustomException.class,
                () -> passwordResetService.confirmReset(email, code, "anotherPassword1!"));
        assertEquals(ErrorCode.INVALID_OR_EXPIRED_PASSWORD_RESET_CODE, exception.getErrorCode());
    }

    private void signUpLocalUser(String email, String password, String name) {
        emailVerificationService.requestCode(email);
        emailVerificationService.confirmCode(email, mailSender.codeFor(email));
        signupService.signup(new SignupRequest(email, password, name, "KR"));
    }

    private String loginJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
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
        public void sendVerificationCode(String email, String code) {
            codes.put(email, code);
        }

        @Override
        public void sendPasswordResetCode(String email, String code) {
            codes.put(email, code);
        }

        String codeFor(String email) {
            return codes.get(email);
        }
    }
}
