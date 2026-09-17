package Timeout.travel_tackle.auth;

import Timeout.travel_tackle.auth.dto.SignupRequest;
import Timeout.travel_tackle.auth.mail.VerificationMailSender;
import Timeout.travel_tackle.auth.service.EmailVerificationService;
import Timeout.travel_tackle.auth.service.PasswordResetService;
import Timeout.travel_tackle.auth.service.SignupService;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {"app.auth.password-reset-expiration-minutes=0"})
@Transactional
@Import(PasswordResetExpiryTests.MailTestConfig.class)
class PasswordResetExpiryTests {

    @Autowired EmailVerificationService emailVerificationService;
    @Autowired SignupService signupService;
    @Autowired PasswordResetService passwordResetService;
    @Autowired CapturingVerificationMailSender mailSender;

    @Test
    void expiredCodeIsRejected() {
        String email = "expiry-user@example.com";
        emailVerificationService.requestCode(email);
        emailVerificationService.confirmCode(email, mailSender.codeFor(email));
        signupService.signup(new SignupRequest(email, "password123!", "만료 테스트", "KR"));

        passwordResetService.requestReset(email);
        String code = mailSender.codeFor(email);
        assertNotNull(code);

        CustomException exception = assertThrows(CustomException.class,
                () -> passwordResetService.confirmReset(email, code, "newPassword1!"));
        assertEquals(ErrorCode.INVALID_OR_EXPIRED_PASSWORD_RESET_CODE, exception.getErrorCode());
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
