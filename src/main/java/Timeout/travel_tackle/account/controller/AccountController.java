package Timeout.travel_tackle.account.controller;

import Timeout.travel_tackle.account.service.AccountDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "이메일 인증 및 일반 회원가입 API")
public class AccountController {

    private final AccountDeletionService accountDeletionService;

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response
    ) {
        accountDeletionService.deleteAccount(jwt.getSubject(), response);
        return ResponseEntity.noContent().build();
    }
}
