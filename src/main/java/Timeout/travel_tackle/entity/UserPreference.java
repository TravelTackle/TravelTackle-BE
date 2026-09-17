package Timeout.travel_tackle.entity;

import Timeout.travel_tackle.entity.Enum.BudgetLevel;
import Timeout.travel_tackle.entity.Enum.InterestTag;
import Timeout.travel_tackle.entity.Enum.PreferredRegion;
import Timeout.travel_tackle.entity.Enum.TravelStyle;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_style")
    private TravelStyle travelStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_level")
    private BudgetLevel budgetLevel;

    @ElementCollection
    @CollectionTable(
            name = "user_preference_interest_tags",
            joinColumns = @JoinColumn(name = "user_preference_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"user_preference_id", "interest_tag"}
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "interest_tag", nullable = false)
    private Set<InterestTag> interestTags = new HashSet<>();

    @ElementCollection
    @CollectionTable(
            name = "user_preference_regions",
            joinColumns = @JoinColumn(name = "user_preference_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"user_preference_id", "region"}
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false)
    private Set<PreferredRegion> preferredRegions = new HashSet<>();

    public UserPreference(User user, TravelStyle travelStyle, BudgetLevel budgetLevel,
                          Set<InterestTag> interestTags, Set<PreferredRegion> preferredRegions) {
        this.user = user;
        this.travelStyle = travelStyle;
        this.budgetLevel = budgetLevel;
        this.interestTags.addAll(interestTags);
        this.preferredRegions.addAll(preferredRegions);
    }

    public Set<InterestTag> getInterestTags() {
        return Collections.unmodifiableSet(interestTags);
    }

    public Set<PreferredRegion> getPreferredRegions() {
        return Collections.unmodifiableSet(preferredRegions);
    }

    public void update(TravelStyle travelStyle, BudgetLevel budgetLevel,
                       Set<InterestTag> interestTags, Set<PreferredRegion> preferredRegions) {
        this.travelStyle = travelStyle;
        this.budgetLevel = budgetLevel;
        this.interestTags.clear();
        this.interestTags.addAll(interestTags);
        this.preferredRegions.clear();
        this.preferredRegions.addAll(preferredRegions);
    }
}
