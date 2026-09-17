package Timeout.travel_tackle.account;

import Timeout.travel_tackle.account.service.AccountDeletionService;
import Timeout.travel_tackle.auth.repository.UserAuthProviderRepository;
import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.cart.repository.CartItemRepository;
import Timeout.travel_tackle.entity.CartItem;
import Timeout.travel_tackle.entity.TripFeedback;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.entity.Enum.BudgetLevel;
import Timeout.travel_tackle.entity.Enum.FeedItemType;
import Timeout.travel_tackle.entity.Enum.InterestTag;
import Timeout.travel_tackle.entity.Enum.PreferredRegion;
import Timeout.travel_tackle.entity.Enum.TravelStyle;
import Timeout.travel_tackle.preference.dto.PreferenceRequest;
import Timeout.travel_tackle.preference.repository.UserPreferenceRepository;
import Timeout.travel_tackle.preference.service.UserPreferenceService;
import Timeout.travel_tackle.trip.dto.CreateFeedbackRequest;
import Timeout.travel_tackle.trip.dto.AddTripItemRequest;
import Timeout.travel_tackle.trip.dto.CreateTripRequest;
import Timeout.travel_tackle.trip.dto.SavedTripResponse;
import Timeout.travel_tackle.trip.dto.TripSummaryResponse;
import Timeout.travel_tackle.trip.repository.SavedTripRepository;
import Timeout.travel_tackle.trip.repository.TripFeedbackRepository;
import Timeout.travel_tackle.trip.repository.TripRepository;
import Timeout.travel_tackle.trip.service.SavedTripService;
import Timeout.travel_tackle.trip.service.TripFeedbackService;
import Timeout.travel_tackle.trip.service.TripService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AccountDeletionServiceTests {

    @Autowired AccountDeletionService accountDeletionService;
    @Autowired UserRepository userRepository;
    @Autowired UserAuthProviderRepository userAuthProviderRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired UserPreferenceRepository userPreferenceRepository;
    @Autowired UserPreferenceService userPreferenceService;
    @Autowired TripRepository tripRepository;
    @Autowired TripService tripService;
    @Autowired TripFeedbackService feedbackService;
    @Autowired TripFeedbackRepository tripFeedbackRepository;
    @Autowired SavedTripService savedTripService;
    @Autowired SavedTripRepository savedTripRepository;
    @Autowired EntityManager entityManager;

    private User owner;
    private User other;
    private User bystander;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("owner@delete.test", "탈퇴할사람", "KR"));
        other = userRepository.save(new User("other@delete.test", "다른사람", "KR"));
        bystander = userRepository.save(new User("bystander@delete.test", "제3자", "KR"));
    }

    @Test
    void deleteAccountRemovesOwnDataAndAnonymizesFeedbackLeftOnOthersTrips() {
        // owner 소유 데이터: 여행계획(공개), 장바구니, 선호도, 남의 여행 저장
        UUID ownerTripId = createPublishedTrip(owner, "owner의 여행");
        cartItemRepository.save(new CartItem(owner, "content-1", "장소", null, "1", null, null, null, null));
        userPreferenceService.create(owner.getId().toString(), new PreferenceRequest(
                TravelStyle.RELAXED, BudgetLevel.LOW, Set.of(InterestTag.FOOD), Set.of(PreferredRegion.SEOUL)));

        UUID otherTripId = createPublishedTrip(other, "other의 여행");
        savedTripService.save(owner.getId(), otherTripId, FeedItemType.PLAN);

        // other가 owner 여행에 남긴 참견 (owner 탈퇴해도 지워져야 함 - trip 자체가 삭제되므로)
        feedbackService.create(other.getId(), ownerTripId,
                new CreateFeedbackRequest("owner 여행에 대한 참견", null, null, List.of()));

        // owner가 other 여행에 남긴 참견 (owner 탈퇴 후에도 익명화되어 남아야 함)
        feedbackService.create(owner.getId(), otherTripId,
                new CreateFeedbackRequest("other 여행에 대한 참견", null, null, List.of()));

        entityManager.flush();
        entityManager.clear();

        accountDeletionService.deleteAccount(owner.getId().toString(), new MockHttpServletResponse());
        entityManager.flush();
        entityManager.clear();

        UUID ownerId = owner.getId();
        assertTrue(userRepository.findById(ownerId).isEmpty());
        assertTrue(cartItemRepository.findAllByUserIdOrderByAddedAtDesc(ownerId).isEmpty());
        assertTrue(userPreferenceRepository.findByUserId(ownerId).isEmpty());
        assertTrue(userAuthProviderRepository.findAllByUserId(ownerId).isEmpty());
        assertTrue(tripRepository.findById(ownerTripId).isEmpty());

        List<TripFeedback> otherTripFeedback = tripFeedbackRepository.findAllByTripId(otherTripId,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertEquals(1, otherTripFeedback.size());
        assertNull(otherTripFeedback.get(0).getAuthor());
        assertEquals("other 여행에 대한 참견", otherTripFeedback.get(0).getContent());

        assertTrue(savedTripRepository.findAllWithOriginalByUser(other).isEmpty()
                || savedTripRepository.findAllWithOriginalByUser(other).stream()
                        .noneMatch(s -> s.getOriginalTrip().getId().equals(ownerTripId)));
        assertFalse(userRepository.existsByEmail("owner@delete.test"));
    }

    @Test
    void deletingOneAccountDoesNotAffectBystanderData() {
        // owner 소유: 공개 여행계획
        UUID ownerTripId = createPublishedTrip(owner, "owner의 여행");

        // bystander 소유: 자기 여행계획, 장바구니, 선호도 + owner 여행을 저장(복사)
        UUID bystanderOwnTripId = createPublishedTrip(bystander, "제3자 본인 여행");
        cartItemRepository.save(new CartItem(bystander, "content-2", "제3자 장소", null, "1", null, null, null, null));
        userPreferenceService.create(bystander.getId().toString(), new PreferenceRequest(
                TravelStyle.MODERATE, BudgetLevel.HIGH, Set.of(InterestTag.HISTORY), Set.of(PreferredRegion.BUSAN)));
        SavedTripResponse scrap = savedTripService.save(bystander.getId(), ownerTripId, FeedItemType.PLAN);
        TripSummaryResponse savedCopy = savedTripService.copy(bystander.getId(), scrap.savedTripId());
        UUID bystanderCopyTripId = savedCopy.id();

        entityManager.flush();
        entityManager.clear();

        accountDeletionService.deleteAccount(owner.getId().toString(), new MockHttpServletResponse());
        entityManager.flush();
        entityManager.clear();

        // owner는 삭제됐지만 bystander 계정/데이터는 전부 그대로여야 한다
        assertTrue(userRepository.findById(bystander.getId()).isPresent());
        assertTrue(tripRepository.findById(bystanderOwnTripId).isPresent());
        assertEquals(1, cartItemRepository.findAllByUserIdOrderByAddedAtDesc(bystander.getId()).size());
        assertTrue(userPreferenceRepository.findByUserId(bystander.getId()).isPresent());

        // owner 원본을 저장(복사)해서 만든 bystander 소유의 복사본 Trip은 독립된 데이터라 그대로 남아있어야 한다
        assertTrue(tripRepository.findById(bystanderCopyTripId).isPresent());

        // 다만 "어디서 저장했는지" 가리키는 SavedTrip 북마크는 원본이 없어졌으므로 사라지는 게 맞다
        assertTrue(savedTripRepository.findAllWithOriginalByUser(bystander).isEmpty());
    }

    private UUID createPublishedTrip(User user, String title) {
        LocalDate date = LocalDate.of(2026, 7, 1);
        UUID tripId = tripService.createTrip(user.getId(), new CreateTripRequest(title, date, date)).id();
        // 모든 일차에 일정이 있어야 공개할 수 있다
        UUID dayId = tripService.getTripDetail(user.getId(), tripId).days().getFirst().id();
        CartItem cartItem = cartItemRepository.save(
                new CartItem(user, "item-" + title, title, null, "1", null, null, null, null));
        tripService.addTripItem(user.getId(), tripId, dayId, new AddTripItemRequest(cartItem.getId(), null, null));
        cartItemRepository.delete(cartItem); // 장바구니 개수 검증에 섞이지 않게 정리 (일정은 스냅샷이라 영향 없음)
        tripService.publishTrip(user.getId(), tripId);
        entityManager.flush();
        return tripId;
    }
}
