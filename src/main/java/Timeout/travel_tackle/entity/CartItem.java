package Timeout.travel_tackle.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(
        columnNames = {"user_id", "tour_api_content_id"}
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

    @Column(name = "cached_title", nullable = false)
    private String cachedTitle; //관광지 이름

    @Column(name = "cached_image_url")
    private String cachedImageUrl; //관광지 사진

    @Column(name = "cached_region_code")
    private String cachedRegionCode; //관광지 지역 코드

    @Column(name = "cached_address")
    private String cachedAddress; //관광지 전체 주소 (지역 라벨 파싱용, 계획에 담을 때 TripItem.address로 이어짐)

    @Column(name = "cached_content_type_id")
    private String cachedContentTypeId; //TourAPI 콘텐츠 타입 (12 관광지, 32 숙박, 39 음식점 등, 테마 분류용)

    @Column(name = "cached_lcls_systm1")
    private String cachedLclsSystm1; //분류체계 대분류 (취향 매칭용)

    @Column(name = "cached_lcls_systm2")
    private String cachedLclsSystm2; //분류체계 중분류

    @Column(name = "cached_lcls_systm3")
    private String cachedLclsSystm3; //분류체계 소분류

    @Column(name = "pet_friendly")
    private Boolean petFriendly; //반려동물 동반 가능 여부 (담을 때 관광공사 반려동물 서비스로 확인, null = 미확인)

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt; //바구니에 담은 시간

    public CartItem(User user, String tourApiContentId, String cachedTitle,
                    String cachedImageUrl, String cachedRegionCode, String cachedContentTypeId,
                    String cachedLclsSystm1, String cachedLclsSystm2, String cachedLclsSystm3,
                    String cachedAddress) {
        this.user = user;
        this.tourApiContentId = tourApiContentId;
        this.cachedTitle = cachedTitle;
        this.cachedImageUrl = cachedImageUrl;
        this.cachedRegionCode = cachedRegionCode;
        this.cachedContentTypeId = cachedContentTypeId;
        this.cachedLclsSystm1 = cachedLclsSystm1;
        this.cachedLclsSystm2 = cachedLclsSystm2;
        this.cachedLclsSystm3 = cachedLclsSystm3;
        this.cachedAddress = cachedAddress;
    }

    public void markPetFriendly(Boolean petFriendly) {
        this.petFriendly = petFriendly;
    }
}
