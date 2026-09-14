package Timeout.travel_tackle.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_cart_items_user_content_service",
        columnNames = {"user_id", "tour_api_content_id", "tour_api_service"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "tour_api_content_id", nullable = false)
    private String tourApiContentId; //Tour API에서 제공하는 관광 컨테츠 고유 ID

    @ColumnDefault("'KorService2'")
    @Column(name = "tour_api_service", nullable = false)
    private String tourApiService; //언어별 TourAPI 서비스(KorService2/EngService2/...) — contentId는 서비스마다 다른 값이라 반드시 함께 저장

    @Column(name = "cached_title", nullable = false)
    private String cachedTitle; //관광지 이름

    @Column(name = "cached_image_url")
    private String cachedImageUrl; //관광지 사진

    @Column(name = "cached_region_code")
    private String cachedRegionCode; //관광지 지역 코드

    @Column(name = "cached_content_type_id")
    private String cachedContentTypeId; //TourAPI 콘텐츠 타입 (12 관광지, 32 숙박, 39 음식점 등, 테마 분류용)

    @Column(name = "cached_lcls_systm1")
    private String cachedLclsSystm1; //분류체계 대분류 (취향 매칭용)

    @Column(name = "cached_lcls_systm2")
    private String cachedLclsSystm2; //분류체계 중분류

    @Column(name = "cached_lcls_systm3")
    private String cachedLclsSystm3; //분류체계 소분류

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt; //바구니에 담은 시간

    public CartItem(User user, String tourApiContentId, String tourApiService, String cachedTitle,
                    String cachedImageUrl, String cachedRegionCode, String cachedContentTypeId,
                    String cachedLclsSystm1, String cachedLclsSystm2, String cachedLclsSystm3) {
        this.user = user;
        this.tourApiContentId = tourApiContentId;
        this.tourApiService = tourApiService;
        this.cachedTitle = cachedTitle;
        this.cachedImageUrl = cachedImageUrl;
        this.cachedRegionCode = cachedRegionCode;
        this.cachedContentTypeId = cachedContentTypeId;
        this.cachedLclsSystm1 = cachedLclsSystm1;
        this.cachedLclsSystm2 = cachedLclsSystm2;
        this.cachedLclsSystm3 = cachedLclsSystm3;
    }
}
