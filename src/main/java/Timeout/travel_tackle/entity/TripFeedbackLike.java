package Timeout.travel_tackle.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip_feedback_likes", uniqueConstraints = @UniqueConstraint(
        columnNames = {"feedback_id", "user_id"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripFeedbackLike {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
    private TripFeedback feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public TripFeedbackLike(TripFeedback feedback, User user) {
        this.feedback = feedback;
        this.user = user;
    }
}
