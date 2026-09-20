package Timeout.travel_tackle.trip.service;

import Timeout.travel_tackle.cart.repository.CartItemRepository;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.tour.service.TourService;
import Timeout.travel_tackle.trip.repository.TripItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 반려동물 동반 여부가 아직 없는(pet_friendly null) 장소를 관광공사 서비스로 확인해 채운다.
 * 이 기능이 생기기 전에 담긴 장소와 시드 데이터용. 장소(contentId)당 호출 1회, 실패한 장소는 다음 실행 때 다시 시도.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetFriendlyBackfillService {

    private final TripItemRepository tripItemRepository;
    private final CartItemRepository cartItemRepository;
    private final TourService tourService;
    private final TransactionTemplate transactionTemplate; // 같은 빈 안에서 호출하므로 @Transactional 대신 명시적으로 묶는다

    /** @return 이번에 확정한 장소 수 */
    public int backfill() {
        Set<String> contentIds = new LinkedHashSet<>(tripItemRepository.findContentIdsWithUnknownPetFriendly());
        contentIds.addAll(cartItemRepository.findContentIdsWithUnknownPetFriendly());
        int filled = 0;
        for (String contentId : contentIds) {
            try {
                fill(contentId, tourService.isPetFriendly(contentId));
                filled++;
            } catch (CustomException e) {
                log.warn("반려동물 동반 여부 확인 실패, 미확인으로 둠: contentId={}, code={}", contentId, e.getErrorCode());
            }
        }
        log.info("반려동물 동반 여부 백필: 대상 {}곳, 확정 {}곳", contentIds.size(), filled);
        return filled;
    }

    private void fill(String contentId, boolean petFriendly) {
        transactionTemplate.executeWithoutResult(status -> {
            tripItemRepository.fillPetFriendly(contentId, petFriendly);
            cartItemRepository.fillPetFriendly(contentId, petFriendly);
        });
    }
}
