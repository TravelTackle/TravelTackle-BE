package Timeout.travel_tackle.chat.controller;
import Timeout.travel_tackle.chat.service.ChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "선호도 기반 추천 챗봇 API (LLM + TourAPI MCP)")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(summary = "추천 챗봇 대화 - 선호도를 반영해 관광지/행사/숙박/맛집을 추천")
    public ChatResponse chat(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatRequest request
    ) {
        String reply = chatService.chat(
                jwt.getSubject(), request.message(), request.conversationId(), request.language());
        return new ChatResponse(reply, request.conversationId());
    }

    /**
     * conversationId는 대화 스레드 구분용(선택). 없으면 사용자 단위로 이어짐.
     * language는 프론트의 UI 선택 언어(ko/en/ja/zh/zh-tw/de/fr/es/ru). 지정하면 그 언어로 답변·검색을
     * 고정하고, 비우면 대화 언어를 자동 감지한다. "새 대화"는 프론트가 새 conversationId를 발급하면 된다.
     */
    public record ChatRequest(@NotBlank String message, String conversationId, String language) {
    }

    public record ChatResponse(String reply, String conversationId) {
    }
}
