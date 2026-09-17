package Timeout.travel_tackle.entity;

import Timeout.travel_tackle.entity.Enum.FeedItemType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "saved_trips", uniqueConstraints = @UniqueConstraint(
        columnNames = {"user_id", "original_trip_id"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedTrip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_trip_id", nullable = false)
    private Trip originalTrip;

    // 스크랩(찜)만 한 상태에서는 비어 있다가, 사용자가 "내 계획으로 복사하기"를 눌러야 채워진다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "copied_trip_id")
    private Trip copiedTrip;

    // 스크랩 버튼을 누른 카드가 PLAN이었는지 RECORD였는지. 해제 후 다시 스크랩하면(행을 다시 만드는 것이라)
    // 그 시점 값으로 자연히 갱신된다 — 별도의 "최신값 갱신" 로직이 필요 없다.
    // ddl-auto update 가 기존 행이 있는 테이블에 NOT NULL 컬럼을 추가할 수 있도록 DB 기본값을 함께 준다
    @ColumnDefault("'PLAN'")
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private FeedItemType sourceType;

    @CreationTimestamp
    @Column(name = "saved_at", nullable = false, updatable = false)
    private LocalDateTime savedAt;

    public SavedTrip(User user, Trip originalTrip, FeedItemType sourceType) {
        this.user = user;
        this.originalTrip = originalTrip;
        this.sourceType = sourceType;
    }

    public void markCopied(Trip copiedTrip) {
        this.copiedTrip = copiedTrip;
    }
}
