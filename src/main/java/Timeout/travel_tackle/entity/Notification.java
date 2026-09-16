package Timeout.travel_tackle.entity;

import Timeout.travel_tackle.entity.Enum.NotificationTarget;
import Timeout.travel_tackle.entity.Enum.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 사용자 알림. 참견 등 다른 사용자의 행동을 받는 사람 기준으로 저장한다.
 * 행위자·계획·참견 정보는 FK 없이 스냅샷으로 두어, 원본이 수정·삭제돼도 "그때 이런 알림이 왔다"가 남는다.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_user_read", columnList = "user_id, is_read, created_at DESC"),
        @Index(name = "idx_notification_trip", columnList = "trip_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 받는 사람

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "trip_title")
    private String tripTitle;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "feedback_id")
    private UUID feedbackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target")
    private NotificationTarget target;

    @Column(name = "day_number")
    private Integer dayNumber;

    @Column(name = "item_title")
    private String itemTitle;

    @Column(length = 200)
    private String preview;

    @ColumnDefault("false")
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Notification feedback(User user, UUID actorId, String actorName,
                                        UUID tripId, String tripTitle, String thumbnailUrl,
                                        UUID feedbackId, NotificationTarget target, Integer dayNumber,
                                        String itemTitle, String preview) {
        Notification n = new Notification();
        n.user = user;
        n.type = NotificationType.FEEDBACK;
        n.actorId = actorId;
        n.actorName = actorName;
        n.tripId = tripId;
        n.tripTitle = tripTitle;
        n.thumbnailUrl = thumbnailUrl;
        n.feedbackId = feedbackId;
        n.target = target;
        n.dayNumber = dayNumber;
        n.itemTitle = itemTitle;
        n.preview = preview;
        return n;
    }

    public static Notification scrap(User user, UUID actorId, String actorName,
                                     UUID tripId, String tripTitle, String thumbnailUrl) {
        Notification n = new Notification();
        n.user = user;
        n.type = NotificationType.SCRAP;
        n.actorId = actorId;
        n.actorName = actorName;
        n.tripId = tripId;
        n.tripTitle = tripTitle;
        n.thumbnailUrl = thumbnailUrl;
        return n;
    }

    public void markRead() {
        this.read = true;
    }
}
