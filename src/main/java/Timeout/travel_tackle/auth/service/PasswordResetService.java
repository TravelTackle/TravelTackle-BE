package Timeout.travel_tackle.auth.service;

import Timeout.travel_tackle.auth.jwt.service.RefreshTokenService;
import Timeout.travel_tackle.auth.mail.VerificationMailSender;
import Timeout.travel_tackle.auth.repository.PasswordResetRepository;
import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.entity.PasswordReset;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int REQUEST_COOLDOWN_SECONDS = 60;
    private static final int HOURLY_REQUEST_LIMIT = 5;

    private final PasswordResetRepository passwordResetRepository;
    private final UserRepository userRepository;
    private final VerificationMailSender verificationMailSender;
    private final VerificationCodeGenerator verificationCodeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final EmailNormalizer emailNormalizer;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.auth.password-reset-expiration-minutes:10}")
    private long expirationMinutes;

    @Transactional
    public void requestReset(String rawEmail) {
        String email = emailNormalizer.normalize(rawEmail);
        LocalDateTime now = LocalDateTime.now();
        validateRequestRate(email, now);

        // 계정 존재 여부와 무관하게 항상 요청 기록을 남긴다(rate-limit 상태를 대칭적으로 유지해
        // "몇 번째 요청부터 429가 뜨는지"로 계정 존재 여부가 드러나는 것을 방지).
        String code = verificationCodeGenerator.generate();
        PasswordReset reset = new PasswordReset(
                email,
                passwordEncoder.encode(code),
                now.plusMinutes(expirationMinutes)
        );
        passwordResetRepository.save(reset);

        userRepository.findByEmail(email)
                .filter(user -> user.getPasswordHash() != null)
                .ifPresent(user -> sendResetCodeWithoutFailingRequest(email, code));
    }

    // 메일 발송 실패가 이 메서드의 트랜잭션을 롤백시키면(위에서 저장한 PasswordReset row까지 함께
    // 사라짐) 실패 여부가 "계정 존재 여부"와 상관관계를 갖게 되어 계정 열거 방지가 깨진다.
    // 그래서 발송 실패는 응답에 반영하지 않고 삼킨다(실패 자체는 mail sender가 이미 로그로 남김).
    private void sendResetCodeWithoutFailingRequest(String email, String code) {
        try {
            verificationMailSender.sendPasswordResetCode(email, code);
        } catch (CustomException exception) {
            // 로그는 SmtpVerificationMailSender에서 이미 남김 — 여기서는 응답/트랜잭션을 지키기 위해 무시
        }
    }

    @Transactional
    public void confirmReset(String rawEmail, String code, String newPassword) {
        String email = emailNormalizer.normalize(rawEmail);
        LocalDateTime now = LocalDateTime.now();

        PasswordReset reset = passwordResetRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_OR_EXPIRED_PASSWORD_RESET_CODE));

        if (reset.isUsed() || reset.isExpired(now)) {
            throw new CustomException(ErrorCode.INVALID_OR_EXPIRED_PASSWORD_RESET_CODE);
        }
        if (reset.isLocked()) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_LOCKED);
        }
        if (!passwordEncoder.matches(code, reset.getCodeHash())) {
            // 이 메서드는 곧바로 예외를 던져 트랜잭션이 롤백되므로, 실패 횟수는
            // 별도 트랜잭션에서 즉시 커밋되는 repository 메서드로 남겨야 실제로 잠금이 걸린다.
            passwordResetRepository.incrementAttempts(reset.getId());
            throw new CustomException(ErrorCode.INVALID_OR_EXPIRED_PASSWORD_RESET_CODE);
        }

        User user = userRepository.findByEmail(email)
                .filter(candidate -> candidate.getPasswordHash() != null)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_OR_EXPIRED_PASSWORD_RESET_CODE));

        user.changePassword(passwordEncoder.encode(newPassword));
        reset.use(now);
        refreshTokenService.revokeAllTokens(user);
    }

    private void validateRequestRate(String email, LocalDateTime now) {
        passwordResetRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .filter(latest -> latest.getCreatedAt().isAfter(now.minusSeconds(REQUEST_COOLDOWN_SECONDS)))
                .ifPresent(latest -> {
                    throw new CustomException(ErrorCode.PASSWORD_RESET_RATE_LIMITED);
                });

        long hourlyRequests = passwordResetRepository.countByEmailAndCreatedAtAfter(
                email,
                now.minusHours(1)
        );
        if (hourlyRequests >= HOURLY_REQUEST_LIMIT) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_RATE_LIMITED);
        }
    }
}
