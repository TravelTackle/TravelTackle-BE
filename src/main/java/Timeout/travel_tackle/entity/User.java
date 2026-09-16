package Timeout.travel_tackle.entity;

import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    // 프론트(LANGUAGES, i18n/index.jsx)·챗봇(ChatRequest.language)과 동일한 언어 코드 집합
    private static final Set<String> SUPPORTED_LANGUAGES =
            Set.of("ko", "en", "ja", "zh", "zh-tw", "de", "fr", "es", "ru");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(nullable = false)
    private String name;

    private String nationality; //사용자 국적 코드

    @Column(name = "credit_balance")
    private int creditBalance; //크레딧 잔액

    @ColumnDefault("0") // 기존 행에 NULL 이 들어가면 primitive int 매핑이 조회 시 실패한다
    @Column(name = "free_trials_used")
    private int freeTrialsUsed; //크레딧 없이 무료로 다른 사용자의 계획을 저장한 횟수

    @ColumnDefault("'ko'")
    @Column(name = "preferred_language")
    private String preferredLanguage = "ko"; //선호 언어 코드 (기기 간 동기화용)

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl; //S3 에 올린 프로필 사진 읽기 URL (없으면 null)

    // 알림 설정 — 값만 저장, 실제 발송 트리거는 아직 미구현
    // ddl-auto update 가 기존 행이 있는 테이블에 NOT NULL 컬럼을 추가할 수 있도록 DB 기본값을 함께 준다
    @ColumnDefault("true")
    @Column(name = "notify_email", nullable = false)
    private boolean notifyEmail = true;

    @ColumnDefault("true")
    @Column(name = "notify_feedback", nullable = false)
    private boolean notifyFeedback = true;

    @ColumnDefault("true")
    @Column(name = "notify_recommend", nullable = false)
    private boolean notifyRecommend = true;

    @ColumnDefault("false")
    @Column(name = "notify_event", nullable = false)
    private boolean notifyEvent = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public User(String email, String name, String nationality) {
        this.email = email;
        this.name = name;
        this.nationality = nationality;
    }

    public static User localUser(String email, String passwordHash, String name, String nationality) {
        User user = new User(email, name, nationality);
        user.passwordHash = passwordHash;
        user.emailVerifiedAt = LocalDateTime.now();
        return user;
    }

    public static User socialUser(String email, String name) {
        User user = new User(email, name, null);
        if (email != null) {
            user.emailVerifiedAt = LocalDateTime.now();
        }
        return user;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        this.name = name;
    }

    public void changeLanguage(String preferredLanguage) {
        if (!SUPPORTED_LANGUAGES.contains(preferredLanguage)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        this.preferredLanguage = preferredLanguage;
    }

    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void removeProfileImage() {
        this.profileImageUrl = null;
    }

    public void updateNotificationSettings(boolean notifyEmail, boolean notifyFeedback,
                                            boolean notifyRecommend, boolean notifyEvent) {
        this.notifyEmail = notifyEmail;
        this.notifyFeedback = notifyFeedback;
        this.notifyRecommend = notifyRecommend;
        this.notifyEvent = notifyEvent;
    }

    public void useFreeTrial() {
        this.freeTrialsUsed++;
    }

    public void changeCreditBalance(int amount) {
        this.creditBalance += amount;
    }
}
