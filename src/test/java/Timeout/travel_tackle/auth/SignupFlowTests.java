package Timeout.travel_tackle.auth;

import Timeout.travel_tackle.auth.dto.SignupRequest;
import Timeout.travel_tackle.auth.dto.SignupResponse;
import Timeout.travel_tackle.auth.mail.VerificationMailSender;
import Timeout.travel_tackle.auth.repository.UserAuthProviderRepository;
import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.auth.service.EmailVerificationService;
import Timeout.travel_tackle.auth.service.SignupService;
import Timeout.travel_tackle.entity.Enum.AuthProvider;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.entity.UserAuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Import(SignupFlowTests.MailTestConfig.class)
class SignupFlowTests {

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private SignupService signupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthProviderRepository userAuthProviderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CapturingVerificationMailSender mailSender;

    @Test
    void verifiedEmailCanCreateLocalUser() {
        String email = "USER@example.com";
        String password = "password123!";

        emailVerificationService.requestCode(email);
        String code = mailSender.getCode("user@example.com");
        emailVerificationService.confirmCode(email, code);

        SignupResponse response = signupService.signup(
                new SignupRequest(email, password, "홍길동", "kr")
        );

        User user = userRepository.findByEmail("user@example.com").orElseThrow();
        UserAuthProvider authProvider = userAuthProviderRepository
                .findByProviderAndProviderUserId(AuthProvider.LOCAL, "user@example.com")
                .orElseThrow();

        assertEquals(user.getId(), response.userId());
        assertEquals("KR", user.getNationality());
        assertNotEquals(password, user.getPasswordHash());
        assertTrue(passwordEncoder.matches(password, user.getPasswordHash()));
        assertNotNull(user.getEmailVerifiedAt());
        assertEquals(user.getId(), authProvider.getUser().getId());
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

        String getCode(String email) {
            return codes.get(email);
        }
    }
}
