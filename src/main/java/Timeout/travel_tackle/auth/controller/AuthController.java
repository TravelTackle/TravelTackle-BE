package Timeout.travel_tackle.auth.controller;

import Timeout.travel_tackle.auth.dto.ChangePasswordRequest;
import Timeout.travel_tackle.auth.dto.EmailVerificationConfirmRequest;
import Timeout.travel_tackle.auth.dto.EmailVerificationRequest;
import Timeout.travel_tackle.auth.dto.CurrentUserResponse;
import Timeout.travel_tackle.auth.dto.LoginRequest;
import Timeout.travel_tackle.auth.dto.NotificationSettingsRequest;
import Timeout.travel_tackle.auth.dto.PasswordResetConfirmRequest;
import Timeout.travel_tackle.auth.dto.PasswordResetRequest;
import Timeout.travel_tackle.auth.dto.SignupRequest;
import Timeout.travel_tackle.auth.dto.SignupResponse;
import Timeout.travel_tackle.auth.dto.UpdateProfileRequest;
import Timeout.travel_tackle.auth.service.EmailVerificationService;
import Timeout.travel_tackle.auth.service.AuthenticationService;
import Timeout.travel_tackle.auth.service.PasswordResetService;
import Timeout.travel_tackle.auth.service.SignupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "이메일 인증 및 일반 회원가입 API")
public class AuthController {

    private final EmailVerificationService emailVerificationService;
    private final SignupService signupService;
    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/email-verifications")
    @Operation(summary = "이메일 인증번호 요청")
    @SecurityRequirements
    public ResponseEntity<Void> requestEmailVerification(
            @Valid @RequestBody EmailVerificationRequest request
    ) {
        emailVerificationService.requestCode(request.email(), request.language());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/email-verifications/confirm")
    @Operation(summary = "이메일 인증번호 확인")
    @SecurityRequirements
    public ResponseEntity<Void> confirmEmailVerification(
            @Valid @RequestBody EmailVerificationConfirmRequest request
    ) {
        emailVerificationService.confirmCode(request.email(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/signup")
    @Operation(summary = "일반 회원가입")
    @SecurityRequirements
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(signupService.signup(request));
    }

    @PostMapping("/login")
    @Operation(summary = "이메일 로그인")
    @SecurityRequirements
    public ResponseEntity<CurrentUserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        return ResponseEntity.ok(authenticationService.login(request, response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Access Token 재발급")
    @SecurityRequirements
    public ResponseEntity<Void> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authenticationService.refresh(request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    @SecurityRequirements
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authenticationService.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "현재 로그인 사용자 조회")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authenticationService.getCurrentUser(jwt.getSubject()));
    }

    @PatchMapping("/me")
    @Operation(summary = "프로필 수정 (닉네임/언어)")
    public ResponseEntity<CurrentUserResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(authenticationService.updateProfile(jwt.getSubject(), request));
    }

    @PutMapping("/notifications")
    @Operation(summary = "알림 설정 전체 저장")
    public ResponseEntity<CurrentUserResponse> updateNotificationSettings(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NotificationSettingsRequest request
    ) {
        return ResponseEntity.ok(authenticationService.updateNotificationSettings(jwt.getSubject(), request));
    }

    @PatchMapping("/password")
    @Operation(summary = "로그인 상태 비밀번호 변경")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response
    ) {
        authenticationService.changePassword(jwt.getSubject(), request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-resets")
    @Operation(summary = "비밀번호 재설정 인증번호 요청")
    @SecurityRequirements
    public ResponseEntity<Void> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-resets/confirm")
    @Operation(summary = "비밀번호 재설정 인증번호 확인 및 새 비밀번호 설정")
    @SecurityRequirements
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        passwordResetService.confirmReset(request.email(), request.code(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
