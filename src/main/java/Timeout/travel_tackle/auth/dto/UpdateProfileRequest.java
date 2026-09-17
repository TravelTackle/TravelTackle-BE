package Timeout.travel_tackle.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * name, preferredLanguage 둘 다 선택값 — 보낸 필드만 반영되는 부분 업데이트.
 * 유효성(빈 값/허용 언어코드) 검증은 User 엔티티에서 수행한다.
 */
public record UpdateProfileRequest(
        @Size(max = 50)
        String name,

        @Size(max = 10)
        String preferredLanguage
) {
}
