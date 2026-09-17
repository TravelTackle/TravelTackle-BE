package Timeout.travel_tackle.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 여행 기록 생성·수정 (multipart/form-data). 사진 파일을 서버가 S3 에 올린 뒤 기록에 붙인다.
 * captions 는 선택이며 photos 와 인덱스로 짝을 맞춘다.
 */
public record TripRecordUploadRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String content,
        @NotEmpty List<MultipartFile> photos,
        List<@Size(max = 500) String> captions
) {
    public String captionAt(int index) {
        return captions != null && index < captions.size() ? captions.get(index) : null;
    }
}
