package Timeout.travel_tackle.trip.dto;

import jakarta.validation.constraints.Size;

// 게시(전체공개) 시 선택적으로 남기는 한 줄 코멘트. body 자체를 생략하면(레거시 클라이언트) 기존 코멘트를 그대로 둔다.
public record PublishTripRequest(
        @Size(max = 100) String comment
) {}
