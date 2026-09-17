package Timeout.travel_tackle.trip.controller;

import Timeout.travel_tackle.trip.dto.TripRecordRequest;
import Timeout.travel_tackle.trip.dto.TripRecordResponse;
import Timeout.travel_tackle.trip.dto.TripRecordUploadRequest;
import Timeout.travel_tackle.trip.service.TripRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/trips/{tripId}/record")
@RequiredArgsConstructor
@Tag(name = "Trip Record", description = "여행 기록(후기) API — 계획당 1개, 내용 필수, 사진 1장 이상")
public class TripRecordController {

    private final TripRecordService tripRecordService;

    @PostMapping
    @Operation(summary = "여행 기록 작성 (내용 필수 + 사진 1장 이상)")
    public ResponseEntity<TripRecordResponse> createRecord(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @Valid @RequestBody TripRecordRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tripRecordService.createRecord(userId, tripId, request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "여행 기록 작성 + 사진 파일 업로드 (multipart: title, content, photos[], captions[])",
            description = "사진 파일을 서버가 S3 에 올린 뒤 기록에 붙인다. jpeg/png/webp, 파일당 10MB 이하.")
    public ResponseEntity<TripRecordResponse> createRecordWithUploads(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @Valid @ModelAttribute TripRecordUploadRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tripRecordService.createRecordWithUploads(userId, tripId, request));
    }

    @GetMapping
    @Operation(summary = "여행 기록 조회")
    public ResponseEntity<TripRecordResponse> getRecord(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tripRecordService.getRecord(userId, tripId));
    }

    @PatchMapping
    @Operation(summary = "여행 기록 수정 (내용·사진 전체 교체)")
    public ResponseEntity<TripRecordResponse> updateRecord(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @Valid @RequestBody TripRecordRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tripRecordService.updateRecord(userId, tripId, request));
    }

    @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "여행 기록 수정 + 사진 파일 업로드 (내용·사진 전체 교체)")
    public ResponseEntity<TripRecordResponse> updateRecordWithUploads(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @Valid @ModelAttribute TripRecordUploadRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tripRecordService.updateRecordWithUploads(userId, tripId, request));
    }

    @DeleteMapping
    @Operation(summary = "여행 기록 삭제")
    public ResponseEntity<Void> deleteRecord(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        tripRecordService.deleteRecord(userId, tripId);
        return ResponseEntity.noContent().build();
    }
}
