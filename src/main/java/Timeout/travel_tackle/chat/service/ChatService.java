package Timeout.travel_tackle.chat.service;
import Timeout.travel_tackle.chat.TourismTools;

import Timeout.travel_tackle.entity.UserPreference;
import Timeout.travel_tackle.global.util.UuidConverter;
import Timeout.travel_tackle.preference.repository.UserPreferenceRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 선호도 기반 추천 챗봇. LLM(OpenAI 호환) + MCP 관광 툴(TourAPI)을 사용한다.
 * 로그인 사용자의 {@link UserPreference}를 시스템 프롬프트에 주입해 "맞춤" 대화를 만든다.
 * 대화 맥락은 인메모리 {@link ChatMemory}로 conversationId 단위로 기억한다(서버 재시작 시 소실).
 */
@Service
public class ChatService {

    private static final int MAX_MEMORY_MESSAGES = 20; // conversationId별 최근 N개 메시지 유지

    private final ChatClient chatClient;
    private final UserPreferenceRepository userPreferenceRepository;
    private final String defaultSystemPrompt;
    private final String topicGuard; // 여행 외 질문 차단 규칙. yaml 과 별개로 항상 맨 앞에 붙는다

    public ChatService(ChatClient.Builder chatClientBuilder,
                       TourismTools tourismTools,
                       UserPreferenceRepository userPreferenceRepository,
                       @Value("${app.chat.default-system-prompt:}") String defaultSystemPrompt,
                       @Value("classpath:prompts/chat-topic-guard.txt") Resource topicGuard) throws IOException {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(MAX_MEMORY_MESSAGES)
                .build();
        this.chatClient = chatClientBuilder
                .defaultTools(tourismTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.userPreferenceRepository = userPreferenceRepository;
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.topicGuard = topicGuard.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * 한 번의 대화. conversationId가 있으면 그 스레드의 이전 맥락을 이어가고,
     * 없으면 사용자 단위(subject)를 대화 키로 사용한다.
     */
    public String chat(String subject, String message, String conversationId, String language) {
        UUID userId = UuidConverter.fromSubject(subject);
        String threadId = StringUtils.hasText(conversationId) ? conversationId : subject;
        String systemPrompt = topicGuard + "\n"
                + defaultSystemPrompt
                + buildLanguageDirective(language)
                + buildPreferenceContext(userId);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, threadId))
                .call()
                .content();
    }

    /**
     * 프론트가 UI 선택 언어를 넘기면 그 언어로 답변·검색을 "고정"한다(감지가 아니라 명시적 지시라 작은
     * 모델에서도 안정적). language가 없으면 빈 문자열 → 시스템 프롬프트의 자동 감지 규칙으로 폴백.
     */
    private String buildLanguageDirective(String language) {
        if (!StringUtils.hasText(language)) {
            return "";
        }
        String code = language.trim().toLowerCase();
        String name = languageName(code);
        return "\n\n[LANGUAGE OVERRIDE] The user's selected language is " + name + " (" + code + "). "
                + "Reply ONLY in " + name + " regardless of the language the user typed in. "
                + "Always pass language=\"" + code + "\" to every tourism tool call.";
    }

    private String languageName(String code) {
        return switch (code) {
            case "en" -> "English";
            case "ja" -> "Japanese";
            case "zh" -> "Simplified Chinese";
            case "zh-tw" -> "Traditional Chinese";
            case "de" -> "German";
            case "fr" -> "French";
            case "es" -> "Spanish";
            case "ru" -> "Russian";
            case "ko", "kr", "ko-kr" -> "Korean";
            default -> code;
        };
    }

    /** 선호도가 있으면 취향 요약을 시스템 프롬프트에 덧붙인다. 없으면 빈 문자열(일반 봇으로 동작). */
    private String buildPreferenceContext(UUID userId) {
        return userPreferenceRepository.findByUserId(userId)
                .map(this::summarize)
                .orElse("");
    }

    private String summarize(UserPreference preference) {
        // 라벨을 영어로 유지 — 작은 모델이 한국어 텍스트에 끌려 응답 언어가 한국어로 새는 것을 방지.
        StringBuilder context = new StringBuilder("\n\n[User's saved travel preferences — consider these when recommending]");
        if (!preference.getInterestTags().isEmpty()) {
            context.append("\n- Interests: ").append(join(preference.getInterestTags()));
        }
        if (!preference.getPreferredRegions().isEmpty()) {
            context.append("\n- Preferred regions: ").append(join(preference.getPreferredRegions()));
        }
        if (preference.getBudgetLevel() != null) {
            context.append("\n- Budget level: ").append(preference.getBudgetLevel());
        }
        if (preference.getTravelStyle() != null) {
            context.append("\n- Travel style: ").append(preference.getTravelStyle());
        }
        context.append("\nIf the user does not specify a region or interest, prioritize the preferences above. "
                + "This block is context only — do NOT let it change the language of your reply.");
        return context.toString();
    }

    private String join(java.util.Collection<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).collect(Collectors.joining(", "));
    }
}
