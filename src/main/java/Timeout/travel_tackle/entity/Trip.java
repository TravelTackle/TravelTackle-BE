package Timeout.travel_tackle.entity;

import Timeout.travel_tackle.entity.Enum.TripStatus;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_published")
    private boolean published; //계획 공개 여부 다른 사용자가 볼 수 있게 pullic이냐 private냐

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status = TripStatus.PLANNING; //여행 진행 상태 계획중 or 여행 완료

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; //계획 생성 날짜

    @Column(name = "updated_at")
    private LocalDateTime updatedAt; //제목/일차/일정 등 실제 내용이 수정된 시각 (단순 조회로는 갱신되지 않음)

    @Column(name = "feedback_notification_dismissed_at")
    private LocalDateTime feedbackNotificationDismissedAt; //참견 알림함 지운 시각 — 이 시각 이후 새 참견이 없으면 모아보기에서 숨김

    // 게시(공개) 시 남기는 한 줄 코멘트 — 여행 기록의 짧은 코멘트와 같은 자리에 피드 카드에 노출된다. 선택 입력.
    @Column(length = 100)
    private String comment;

    public Trip(User user, String title, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new CustomException(ErrorCode.INVALID_TRIP_DATE_RANGE);
        }
        this.user = user;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void updateSchedule(String title, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new CustomException(ErrorCode.INVALID_TRIP_DATE_RANGE); //종료일이 시작일 보단 빠르면 예외 발생
        }
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        touch();
    }

    // Day/일정처럼 Trip 소유가 아닌 연관 엔티티가 바뀔 때, 실제 내용 수정 시점을 명시적으로 기록하기 위해 호출한다.
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public void publish() {
        this.published = true;
    }

    public void unpublish() {
        this.published = false;
    }

    public void complete() {
        this.status = TripStatus.COMPLETED;
    }

    public void dismissFeedbackNotifications() {
        this.feedbackNotificationDismissedAt = LocalDateTime.now();
    }

    public void updateComment(String comment) {
        this.comment = comment;
    }
}
