package Timeout.travel_tackle.tour;

import Timeout.travel_tackle.entity.Enum.BudgetLevel;
import Timeout.travel_tackle.entity.Enum.InterestTag;
import Timeout.travel_tackle.entity.Enum.PreferredRegion;
import Timeout.travel_tackle.entity.Enum.TravelStyle;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.entity.UserPreference;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.preference.repository.UserPreferenceRepository;
import Timeout.travel_tackle.tour.client.TourApiClient;
import Timeout.travel_tackle.tour.dto.RecommendationDtos.RecommendedSection;
import Timeout.travel_tackle.tour.dto.RecommendationDtos.RecommendationsResponse;
import Timeout.travel_tackle.tour.dto.TourDtos.ContentSummary;
import Timeout.travel_tackle.tour.dto.TourDtos.Page;
import Timeout.travel_tackle.tour.recommendation.service.RecommendationService;
import Timeout.travel_tackle.tour.service.TourService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceTests {

    @Mock TourService tourService;
    @Mock UserPreferenceRepository userPreferenceRepository;
    @InjectMocks RecommendationService recommendationService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void petFriendlyTagAddsDedicatedSectionFromPetServiceRightAfterPersonal() {
        stubPreference(Set.of(InterestTag.PET_FRIENDLY, InterestTag.NATURE));
        when(tourService.getFilteredContents(any(), any(), any(), any(), anyInt())).thenReturn(summaries("일반", 6));
        when(tourService.getFestivals(any(), any(), any(), anyInt(), anyInt())).thenReturn(new Page<>(List.of(), 1, 20, 0));
        when(tourService.getFilteredContentsInLanguage(eq(TourApiClient.PET_SERVICE), eq("26"), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(summaries("부산 반려동물", 7));

        RecommendationsResponse response = recommendationService.getRecommendations(userId.toString());

        List<String> ids = response.sections().stream().map(RecommendedSection::sectionId).toList();
        assertEquals(List.of("personal", "pet", "food", "cafe", "festival"), ids);
        RecommendedSection pet = response.sections().get(1);
        assertEquals("부산 반려동물과 함께", pet.title());
        assertEquals(7, pet.items().size());
        assertTrue(pet.items().stream().allMatch(i -> i.title().startsWith("부산 반려동물")));
        // 반려동물 태그는 맞춤 추천의 관심사 필터로는 쓰지 않는다 (필터 없는 파라미터로 호출되지 않아야 함)
        verify(tourService, never()).getFilteredContents(any(), isNull(), isNull(), isNull(), anyInt());
    }

    @Test
    void petSectionFallsBackToNationwideWhenRegionIsThinAndToEmptyOnFailure() {
        stubPreference(Set.of(InterestTag.PET_FRIENDLY));
        when(tourService.getFilteredContents(any(), any(), any(), any(), anyInt())).thenReturn(summaries("일반", 6));
        when(tourService.getFestivals(any(), any(), any(), anyInt(), anyInt())).thenReturn(new Page<>(List.of(), 1, 20, 0));
        when(tourService.getFilteredContentsInLanguage(eq(TourApiClient.PET_SERVICE), eq("26"), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(summaries("부산", 2)); // 5개 미만이면 전국으로
        when(tourService.getFilteredContentsInLanguage(eq(TourApiClient.PET_SERVICE), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(summaries("전국", 9));

        RecommendedSection pet = recommendationService.getRecommendations(userId.toString()).sections().get(1);
        assertEquals("pet", pet.sectionId());
        assertTrue(pet.items().stream().allMatch(i -> i.title().startsWith("전국")));

        when(tourService.getFilteredContentsInLanguage(eq(TourApiClient.PET_SERVICE), any(), any(), any(), any(), anyInt()))
                .thenThrow(new CustomException(ErrorCode.TOUR_API_UNAVAILABLE));
        RecommendedSection failed = recommendationService.getRecommendations(userId.toString()).sections().get(1);
        assertEquals("pet", failed.sectionId());
        assertTrue(failed.items().isEmpty()); // 반려동물 서비스 장애여도 홈 추천 전체는 살아 있다
    }

    @Test
    void withoutPetTagThereIsNoPetSection() {
        stubPreference(Set.of(InterestTag.NATURE));
        when(tourService.getFilteredContents(any(), any(), any(), any(), anyInt())).thenReturn(summaries("일반", 6));
        when(tourService.getFestivals(any(), any(), any(), anyInt(), anyInt())).thenReturn(new Page<>(List.of(), 1, 20, 0));

        List<String> ids = recommendationService.getRecommendations(userId.toString()).sections().stream()
                .map(RecommendedSection::sectionId).toList();
        assertEquals(List.of("personal", "food", "cafe", "festival"), ids);
        verify(tourService, never()).getFilteredContentsInLanguage(any(), any(), any(), any(), any(), anyInt());
    }

    private void stubPreference(Set<InterestTag> tags) {
        UserPreference preference = new UserPreference(new User("p@test.com", "테스터", "KR"),
                TravelStyle.RELAXED, BudgetLevel.MEDIUM, tags, Set.of(PreferredRegion.BUSAN));
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
    }

    private List<ContentSummary> summaries(String prefix, int n) {
        return IntStream.range(0, n).mapToObj(i -> new ContentSummary(prefix + "-" + i, "12", prefix + " " + i,
                "주소", "6", null, null, null, null, null, 129.0, 35.0, null)).toList();
    }
}
