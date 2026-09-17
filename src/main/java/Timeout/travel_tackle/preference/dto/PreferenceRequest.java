package Timeout.travel_tackle.preference.dto;

import Timeout.travel_tackle.entity.Enum.BudgetLevel;
import Timeout.travel_tackle.entity.Enum.InterestTag;
import Timeout.travel_tackle.entity.Enum.PreferredRegion;
import Timeout.travel_tackle.entity.Enum.TravelStyle;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record PreferenceRequest(
        @NotNull TravelStyle travelStyle,
        @NotNull BudgetLevel budgetLevel,
        @NotEmpty Set<InterestTag> interestTags,
        @NotEmpty Set<PreferredRegion> preferredRegions
) {
}
