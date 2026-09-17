package Timeout.travel_tackle.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip_feedbacks", indexes = {
        @Index(name = "idx_feedback_trip", columnList = "trip_id, created_at DESC"),
        @Index(name = "idx_feedback_day", columnList = "trip_day_id"),
        @Index(name = "idx_feedback_item", columnList = "trip_item_id"),
        @Index(name = "idx_feedback_author", columnList = "author_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // null = Trip 전체 피드백, not-null = TripDay 단위 피드백
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_day_id")
    private TripDay tripDay;

    // null = Trip/Day 피드백, not-null = TripItem 단위 피드백
    // tripDay와 tripItem 동시 설정 불가 (서비스 레이어에서 검증)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_item_id")
    private TripItem tripItem;

    // 작성자 탈퇴 시 참견 자체는 남기고 author만 null로 익명화한다 (AccountDeletionService 참고)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public TripFeedback(Trip trip, TripDay tripDay, TripItem tripItem, User author, String content) {
        this.trip = trip;
        this.tripDay = tripDay;
        this.tripItem = tripItem;
        this.author = author;
        this.content = content;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void markRead() {
        this.read = true;
    }

    public void clearTripDay() {
        this.tripDay = null;
    }

    public void clearTripItem() {
        this.tripItem = null;
    }
}
