package Timeout.travel_tackle.trip.controller;

import Timeout.travel_tackle.entity.Enum.FeedItemType;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.trip.dto.FeedItemResponse;
import Timeout.travel_tackle.trip.dto.FeedSort;
import Timeout.travel_tackle.trip.dto.PublicTripDetailResponse;
import Timeout.travel_tackle.trip.dto.RegionCountResponse;
import Timeout.travel_tackle.trip.dto.UserProfileResponse;
import Timeout.travel_tackle.trip.service.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
@Tag(name = "Feed", description = "공개 여행 피드 API")
public class FeedController {

    private static final int MAX_PAGE_SIZE = 50;

    private final FeedService feedService;

    @GetMapping
    @Operation(summary = "공개 여행 피드 조회",
            description = "page/size/sort(latest|oldest|popular|relevance) + keyword 검색, region(지역 라벨 정확 일치), type(PLAN|RECORD) 필터. 모두 조합 가능")
    public ResponseEntity<Page<FeedItemResponse>> getFeed(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String type
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        boolean hasKeyword = StringUtils.hasText(keyword);
        FeedSort feedSort = FeedSort.from(sort);
        if (feedSort == FeedSort.RELEVANCE && !hasKeyword) {
            feedSort = FeedSort.LATEST; // 키워드 없이 relevance 요청 시 최신순으로 대체
        }

        Pageable pageable = PageRequest.of(safePage, safeSize); // 정렬은 QueryDSL 쿼리 안에서 처리

        UUID userId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
        return ResponseEntity.ok(feedService.getFeed(pageable, feedSort, keyword, region, parseType(type), userId));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "특정 사용자의 공개 프로필 피드 조회 (/mypage가 아닌 외부 열람용, keyword 검색 미지원, sort=latest|oldest|popular)")
    public ResponseEntity<Page<FeedItemResponse>> getUserFeed(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        FeedSort feedSort = FeedSort.from(sort);
        if (feedSort == FeedSort.RELEVANCE) {
            feedSort = FeedSort.LATEST; // keyword 검색이 없으니 relevance는 의미가 없다
        }

        Pageable pageable = sortedPageable(safePage, safeSize, feedSort);
        UUID viewerId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
        return ResponseEntity.ok(feedService.getUserFeed(userId, pageable, feedSort, viewerId));
    }

    @GetMapping("/users/{userId}/profile")
    @Operation(summary = "특정 사용자의 공개 프로필 요약 (이름/프로필사진/공개 계획·기록 수 — 이메일 등 비공개 정보 없음)")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(feedService.getUserProfile(userId));
    }

    private FeedItemType parseType(String type) {
        if (!StringUtils.hasText(type)) {
            return null; // 계획·기록 모두
        }
        try {
            return FeedItemType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private Pageable sortedPageable(int page, int size, FeedSort sort) {
        if (sort == FeedSort.OLDEST) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        }
        if (sort == FeedSort.LATEST) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return PageRequest.of(page, size);
    }

    @GetMapping("/regions")
    @Operation(summary = "인기 지역 집계",
            description = "공개 계획에 포함된 지역별 계획 수(계획당 지역 1회, 생성일 기준). from/to(YYYY-MM-DD)를 둘 다 비우면 이번 달, size 기본 10·최대 50")
    public ResponseEntity<List<RegionCountResponse>> getRegionCounts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ResponseEntity.ok(feedService.getRegionCounts(from, to, safeSize));
    }

    @GetMapping("/{tripId}")
    @Operation(summary = "공개 여행 상세 조회 (일정 + 사진 + 작성자)")
    public ResponseEntity<PublicTripDetailResponse> getPublicTripDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId
    ) {
        UUID userId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
        return ResponseEntity.ok(feedService.getPublicTripDetail(tripId, userId));
    }
}
