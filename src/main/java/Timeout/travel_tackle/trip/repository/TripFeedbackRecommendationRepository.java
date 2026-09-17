package Timeout.travel_tackle.trip.repository;

import Timeout.travel_tackle.entity.TripFeedbackRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripFeedbackRecommendationRepository extends JpaRepository<TripFeedbackRecommendation, UUID> {

    List<TripFeedbackRecommendation> findAllByFeedbackId(UUID feedbackId);

    @Query("SELECT r FROM TripFeedbackRecommendation r JOIN FETCH r.feedback f JOIN FETCH f.trip t JOIN FETCH t.user WHERE r.id = :id")
    Optional<TripFeedbackRecommendation> findByIdWithTripOwner(@Param("id") UUID id);

    void deleteAllByFeedbackId(UUID feedbackId);

    void deleteAllByFeedbackIdIn(List<UUID> feedbackIds);
}
