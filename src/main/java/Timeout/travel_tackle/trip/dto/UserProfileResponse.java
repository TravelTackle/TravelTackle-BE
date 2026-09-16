package Timeout.travel_tackle.trip.dto;

import Timeout.travel_tackle.entity.User;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String name,
        String profileImageUrl,
        long planCount,
        long recordCount
) {
    public static UserProfileResponse of(User user, long planCount, long recordCount) {
        return new UserProfileResponse(user.getId(), user.getName(), user.getProfileImageUrl(), planCount, recordCount);
    }
}
