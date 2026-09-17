package Timeout.travel_tackle.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "trip_feedback_recommendations", indexes = {
        @Index(name = "idx_recommendation_feedback", columnList = "feedback_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripFeedbackRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
    private TripFeedback feedback;

    @Column(name = "tour_api_content_id", nullable = false)
    private String tourApiContentId;

    @Column(name = "cached_title", nullable = false)
    private String cachedTitle;

    @Column(name = "cached_image_url")
    private String cachedImageUrl;

    @Column(name = "cached_area_code")
    private String cachedAreaCode;

    public TripFeedbackRecommendation(TripFeedback feedback, String tourApiContentId,
                                      String cachedTitle, String cachedImageUrl, String cachedAreaCode) {
        this.feedback = feedback;
        this.tourApiContentId = tourApiContentId;
        this.cachedTitle = cachedTitle;
        this.cachedImageUrl = cachedImageUrl;
        this.cachedAreaCode = cachedAreaCode;
    }
}
