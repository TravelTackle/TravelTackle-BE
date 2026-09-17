package Timeout.travel_tackle.preference.dto;

import Timeout.travel_tackle.entity.Enum.BudgetLevel;
import Timeout.travel_tackle.entity.Enum.InterestTag;
import Timeout.travel_tackle.entity.Enum.PreferredRegion;
import Timeout.travel_tackle.entity.Enum.TravelStyle;
import Timeout.travel_tackle.entity.UserPreference;

import java.util.Set;

public record PreferenceResponse(
        TravelStyle travelStyle,
        BudgetLevel budgetLevel,
        Set<InterestTag> interestTags,
        Set<PreferredRegion> preferredRegions
) {
    public static PreferenceResponse from(UserPreference preference) {
        return new PreferenceResponse(
                preference.getTravelStyle(),
                preference.getBudgetLevel(),
                preference.getInterestTags(),
                preference.getPreferredRegions()
        );
    }
}
