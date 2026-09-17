package Timeout.travel_tackle.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 여행 기록 생성·수정 요청.
 * 내용(content)은 필수, 사진(photos)은 최소 1장 필수(여러 장 가능).
 */
public record TripRecordRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String content,
        @NotEmpty @Valid List<PhotoEntry> photos
) {
    public record PhotoEntry(
            @NotBlank String imageUrl,
            @Size(max = 500) String caption
    ) {}
}
