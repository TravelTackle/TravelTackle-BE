package Timeout.travel_tackle.auth.jwt.service;

import Timeout.travel_tackle.auth.repository.RefreshTokenRepository;
import Timeout.travel_tackle.auth.jwt.JwtProperties;
import Timeout.travel_tackle.entity.RefreshToken;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService { //refresh 관련

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthTokens issueTokens(User user) {
        String refreshToken = generateToken();
        saveRefreshToken(user, refreshToken);
        return new AuthTokens(jwtService.createAccessToken(user), refreshToken);
    }

    @Transactional
    public AuthTokens refreshTokens(String refreshToken) {
        RefreshToken storedToken = findRefreshToken(refreshToken);
        validateRefreshToken(storedToken);
        storedToken.revoke(LocalDateTime.now());
        return issueTokens(storedToken.getUser());
    }

    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(LocalDateTime.now()));
    }

    @Transactional
    public void revokeAllTokens(User user) {
        refreshTokenRepository.revokeAllActiveByUser(user, LocalDateTime.now());
    }

    private void saveRefreshToken(User user, String refreshToken) {
        LocalDateTime expiresAt = LocalDateTime.now().plus(properties.refreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(user, hash(refreshToken), expiresAt));
    }

    private RefreshToken findRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        return refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    private void validateRefreshToken(RefreshToken refreshToken) {
        if (refreshToken.isRevoked()) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (refreshToken.isExpired(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedToken = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedToken);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record AuthTokens(String accessToken, String refreshToken) {
    }
}
