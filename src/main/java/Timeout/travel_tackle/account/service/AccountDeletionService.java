package Timeout.travel_tackle.account.service;

import Timeout.travel_tackle.auth.jwt.AuthCookieService;
import Timeout.travel_tackle.auth.repository.RefreshTokenRepository;
import Timeout.travel_tackle.auth.repository.UserAuthProviderRepository;
import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.image.service.ImageStorageService;
import Timeout.travel_tackle.notification.repository.NotificationRepository;
import Timeout.travel_tackle.cart.repository.CartItemRepository;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.global.util.UuidConverter;
import Timeout.travel_tackle.preference.repository.UserPreferenceRepository;
import Timeout.travel_tackle.trip.repository.SavedTripRepository;
import Timeout.travel_tackle.trip.repository.TripFeedbackLikeRepository;
import Timeout.travel_tackle.trip.repository.TripFeedbackRepository;
import Timeout.travel_tackle.trip.repository.TripRepository;
import Timeout.travel_tackle.trip.service.TripService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 회원탈퇴 — 여러 기능 패키지(trip/cart/preference/auth)에 걸친 데이터를 한 번에 정리하는
 * 조합 지점이라 auth가 아닌 별도 패키지(account)에 둔다. auth/trip/cart/preference 모두 이 패키지에
 * 의존받지 않으므로, 이 패키지가 그들에게 의존해도 기능 패키지 간 순환 의존(ArchitectureTest)이 생기지 않는다.
 *
 * 본인 소유 데이터는 삭제하고, 남의 데이터에 걸쳐있는 흔적(남의 여행계획에 남긴 참견)은
 * 삭제 대신 author를 null로 남겨 익명화한다 (FeedbackResponse.of가 "탈퇴한 사용자"로 표시).
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripService tripService;
    private final TripFeedbackRepository tripFeedbackRepository;
    private final TripFeedbackLikeRepository tripFeedbackLikeRepository;
    private final SavedTripRepository savedTripRepository;
    private final CartItemRepository cartItemRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationRepository notificationRepository;
    private final ImageStorageService imageStorageService;
    private final AuthCookieService authCookieService;

    @Transactional
    public void deleteAccount(String subject, HttpServletResponse response) {
        UUID userId = UuidConverter.fromSubject(subject);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHENTICATED));

        // 1. 본인 소유 여행계획 삭제 — 일정/기록/사진/그 여행에 달린 참견/원본으로 저장된 남의 복사본까지 연쇄 처리
        tripRepository.findAllByUserOrderByCreatedAtDesc(user)
                .forEach(trip -> tripService.deleteTrip(userId, trip.getId()));

        // 2. 남의 여행계획에 내가 남긴 참견은 삭제하지 않고 author만 익명화
        tripFeedbackRepository.anonymizeByAuthorId(userId);

        // 2b. 남의 참견에 내가 누른 좋아요는 익명화할 의미가 없어 그냥 삭제
        tripFeedbackLikeRepository.deleteAllByUserId(userId);

        // 3. 내가 저장한 남의 여행 복사본 삭제
        savedTripRepository.deleteAllByUser(user);

        // 4. 장바구니 / 선호도 / 소셜 연동 / 로그인 세션 삭제
        cartItemRepository.deleteAllByUserId(userId);
        userPreferenceRepository.deleteByUserId(userId);
        userAuthProviderRepository.deleteAllByUserId(userId);
        refreshTokenRepository.deleteAllByUser(user);
        notificationRepository.deleteAllByUser(user);
        if (user.getProfileImageUrl() != null) {
            imageStorageService.deleteAfterCommit(userId, List.of(user.getProfileImageUrl()), ImageStorageService.Kind.PROFILE);
        }

        // user_id를 참조하는 자식 row들의 삭제를 users 삭제보다 먼저 DB에 반영 (FK 제약 순서 보장)
        userRepository.flush();

        // 5. 계정 삭제 + 로그인 쿠키 정리
        userRepository.delete(user);
        authCookieService.clearTokens(response);
    }
}
