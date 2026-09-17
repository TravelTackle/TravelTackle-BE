package Timeout.travel_tackle.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPhoto { // 기록(TripRecord)에 첨부된 사진. 이미지 외부 저장 후 URL만 관리

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private TripRecord record;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(length = 500)
    private String caption; // 사진 설명(선택)

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    public TripPhoto(TripRecord record, String imageUrl, String caption) {
        this.record = record;
        this.imageUrl = imageUrl;
        this.caption = caption;
    }

    public void updateCaption(String caption) {
        this.caption = caption;
    }
}
