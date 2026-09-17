package Timeout.travel_tackle.auth.dto;

import Timeout.travel_tackle.entity.Enum.AuthProvider;
import Timeout.travel_tackle.entity.User;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String email,
        String name,
        String nationality,
        String preferredLanguage,
        String profileImageUrl,
        boolean notifyEmail,
        boolean notifyFeedback,
        boolean notifyRecommend,
        boolean notifyEvent,
        List<AuthProvider> authProviders
) {
    public static CurrentUserResponse from(User user, List<AuthProvider> authProviders) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNationality(),
                user.getPreferredLanguage(),
                user.getProfileImageUrl(),
                user.isNotifyEmail(),
                user.isNotifyFeedback(),
                user.isNotifyRecommend(),
                user.isNotifyEvent(),
                authProviders
        );
    }
}
