package Timeout.travel_tackle.auth.service;

import Timeout.travel_tackle.auth.mail.VerificationMailSender;
import Timeout.travel_tackle.auth.repository.EmailVerificationRepository;
import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.entity.EmailVerification;
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
public class EmailVerificationService {

    private static final int REQUEST_COOLDOWN_SECONDS = 60;
    private static final int HOURLY_REQUEST_LIMIT = 5;

    private final EmailVerificationRepository emailVerificationRepository;
    private final UserRepository userRepository;
    private final VerificationMailSender verificationMailSender;
    private final VerificationCodeGenerator verificationCodeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final EmailNormalizer emailNormalizer;

    @Value("${app.auth.email-verification-expiration-minutes:10}")
    private long expirationMinutes;

    @Transactional
    public void requestCode(String rawEmail, String language) {
        String email = emailNormalizer.normalize(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        LocalDateTime now = LocalDateTime.now();
        validateRequestRate(email, now);

        String code = verificationCodeGenerator.generate();
        EmailVerification verification = new EmailVerification(
                email,
                passwordEncoder.encode(code),
                now.plusMinutes(expirationMinutes)
        );
        emailVerificationRepository.save(verification);
        verificationMailSender.sendVerificationCode(email, code, language);
    }

    @Transactional
    public void confirmCode(String rawEmail, String code) {
        String email = emailNormalizer.normalize(rawEmail);
        EmailVerification verification = findLatest(email);
        LocalDateTime now = LocalDateTime.now();

        validateNotUsedOrExpired(verification, now);
        if (!passwordEncoder.matches(code, verification.getCodeHash())) {
            throw new CustomException(ErrorCode.INVALID_EMAIL_VERIFICATION_CODE);
        }
        if (!verification.isVerified()) {
            verification.verify(now);
        }
    }

    EmailVerification getVerifiedAndUsable(String email, LocalDateTime now) {
        EmailVerification verification = findLatest(emailNormalizer.normalize(email));
        validateNotUsedOrExpired(verification, now);
        if (!verification.isVerified()) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        return verification;
    }

    private EmailVerification findLatest(String email) {
        return emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));
    }

    private void validateRequestRate(String email, LocalDateTime now) {
        emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .filter(latest -> latest.getCreatedAt().isAfter(now.minusSeconds(REQUEST_COOLDOWN_SECONDS)))
                .ifPresent(latest -> {
                    throw new CustomException(ErrorCode.EMAIL_VERIFICATION_RATE_LIMITED);
                });

        long hourlyRequests = emailVerificationRepository.countByEmailAndCreatedAtAfter(
                email,
                now.minusHours(1)
        );
        if (hourlyRequests >= HOURLY_REQUEST_LIMIT) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_RATE_LIMITED);
        }
    }

    private void validateNotUsedOrExpired(EmailVerification verification, LocalDateTime now) {
        if (verification.isUsed()) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_ALREADY_USED);
        }
        if (verification.isExpired(now)) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }
    }
}
