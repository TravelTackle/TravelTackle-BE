package Timeout.travel_tackle.preference.repository;

import Timeout.travel_tackle.entity.UserPreference;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {

    @EntityGraph(attributePaths = {"interestTags", "preferredRegions"})
    Optional<UserPreference> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
