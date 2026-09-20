package Timeout.travel_tackle.trip.dto;

import java.util.List;

/**
 * 계획 단위 반려동물 동반 요약. all 은 장소가 하나 이상 있고 전부 동반 가능일 때만 true (미확인 null 은 불가로 본다).
 */
public record PetFriendlySummary(boolean all, int count, int total) {

    public static PetFriendlySummary of(List<TripDayResponse> days) {
        int total = 0;
        int count = 0;
        for (TripDayResponse day : days) {
            for (TripItemResponse item : day.items()) {
                total++;
                if (Boolean.TRUE.equals(item.petFriendly())) {
                    count++;
                }
            }
        }
        return new PetFriendlySummary(total > 0 && count == total, count, total);
    }
}
