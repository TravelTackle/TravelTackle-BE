# 엔티티 정의 문서


---

## User
**테이블**: `users`  
**설명**: 서비스 이용 사용자 정보

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| email | email | String | 이메일 |
| passwordHash | password_hash | String | 일반 로그인 비밀번호 해시 |
| emailVerifiedAt | email_verified_at | LocalDateTime | 이메일 인증 완료 일시 |
| name | name | String | 이름 |
| nationality | nationality | String | 국적 |
| creditBalance | credit_balance | int | 보유 크레딧 |
| freeTrialsUsed | free_trials_used | int | 무료 체험 사용 횟수 |
| preferredLanguage | preferred_language | String | 선호 언어 코드 (기본값 "ko", 기기 간 동기화용) |
| profileImageUrl | profile_image_url | String(1000) | S3 프로필 사진 읽기 URL (없으면 null). 교체·삭제·탈퇴 시 이전 객체는 커밋 후 S3 에서 삭제 |
| notifyEmail | notify_email | boolean | 이메일 알림 허용 여부 (기본 true, 발송 트리거는 미구현) |
| notifyFeedback | notify_feedback | boolean | 활동 알림(참견·스크랩) 허용 여부 (기본 true). 꺼두면 Notification 을 만들지 않는다 |
| notifyRecommend | notify_recommend | boolean | 여행 추천 알림 허용 여부 (기본 true, 발송 트리거는 미구현) |
| notifyEvent | notify_event | boolean | 이벤트 알림 허용 여부 (기본 false, 발송 트리거는 미구현) |
| createdAt | created_at | LocalDateTime | 가입일시 |

**연관관계**
- `UserPreference` 1:1 (sets)
- `CartItem` 1:N (adds)
- `CreditTransaction` 1:N (has)
- `Trip` 1:N (creates)
- `SavedTrip` 1:N
- `UserAuthProvider` 1:N

---

## UserAuthProvider
**테이블**: `user_auth_providers`  
**설명**: 사용자에게 연결된 일반·소셜 로그인 계정

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| user | user_id | UUID (FK) | 연결된 사용자 |
| provider | provider | AuthProvider | LOCAL, KAKAO, GOOGLE, APPLE |
| providerUserId | provider_user_id | String | 인증 제공자의 고유 사용자 ID |
| createdAt | created_at | LocalDateTime | 계정 연결 일시 |

> `(provider, provider_user_id)`와 `(user_id, provider)` 조합은 각각 유일합니다.

---

## EmailVerification
**테이블**: `email_verifications`  
**설명**: 일반 회원가입을 위한 이메일 인증 요청

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| email | email | String | 인증 대상 이메일 |
| codeHash | code_hash | String | 원문을 저장하지 않은 인증 코드 해시 |
| expiresAt | expires_at | LocalDateTime | 인증 만료 일시 |
| verifiedAt | verified_at | LocalDateTime | 인증 완료 일시 |
| usedAt | used_at | LocalDateTime | 회원가입에 사용된 일시 |
| createdAt | created_at | LocalDateTime | 인증 요청 일시 |

---

## UserPreference
**테이블**: `user_preferences`  
**설명**: 사용자 여행 취향 설정

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| user | user_id | UUID (FK) | 사용자 참조 |
| travelStyle | travel_style | String | 여행 스타일 |
| budgetLevel | budget_level | String | 예산 수준 |
| preferredRegion | preferred_region | String | 선호 지역 |
| interestTags | 별도 테이블 | Set<InterestTag> | 관심 태그 목록 |

> 관심 태그는 `user_preference_interest_tags` 테이블에 enum 문자열로 저장됩니다.

---

## CartItem
**테이블**: `cart_items`  
**설명**: 사용자가 장바구니에 담은 여행 콘텐츠

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| user | user_id | UUID (FK) | 사용자 참조 |
| tourApiContentId | tour_api_content_id | String | 투어 API 콘텐츠 ID |
| cachedTitle | cached_title | String | 캐시된 콘텐츠 제목 |
| cachedImageUrl | cached_image_url | String | 캐시된 이미지 URL |
| cachedRegionCode | cached_region_code | String | 캐시된 지역 코드 |
| cachedContentTypeId | cached_content_type_id | String | 캐시된 TourAPI 콘텐츠 타입 (12 관광지, 32 숙박, 39 음식점 등) |
| cachedLclsSystm1 | cached_lcls_systm1 | String | 캐시된 분류체계 대분류 |
| cachedLclsSystm2 | cached_lcls_systm2 | String | 캐시된 분류체계 중분류 |
| cachedLclsSystm3 | cached_lcls_systm3 | String | 캐시된 분류체계 소분류 |
| petFriendly | pet_friendly | Boolean | 반려동물 동반 가능 여부 (담을 때 관광공사 반려동물 서비스로 확인, null = 미확인) |
| addedAt | added_at | LocalDateTime | 장바구니 추가 일시 |

---

## CreditTransaction
**테이블**: `credit_transactions`  
**설명**: 사용자 크레딧 변동 이력

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| user | user_id | UUID (FK) | 사용자 참조 |
| amount | amount | int | 변동 크레딧 양 (양수: 충전, 음수: 사용) |
| reason | reason | String | 변동 사유 |
| createdAt | created_at | LocalDateTime | 발생 일시 |

---

## Trip
**테이블**: `trips`  
**설명**: 사용자가 생성한 여행 계획

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| user | user_id | UUID (FK) | 작성자 참조 |
| title | title | String | 여행 제목 |
| startDate | start_date | LocalDate | 여행 시작일 |
| endDate | end_date | LocalDate | 여행 종료일 |
| published | is_published | boolean | 공개 여부 |
| createdAt | created_at | LocalDateTime | 생성 일시 |
| feedbackNotificationDismissedAt | feedback_notification_dismissed_at | LocalDateTime (nullable) | 참견 알림함 지운 시각 — 이후 새 참견 없으면 모아보기에서 숨김 |

> Lombok `@Getter`와의 충돌 방지를 위해 Java 필드명은 `published`, 컬럼명은 `is_published`로 분리.

**연관관계**
- `TripDay` 1:N (contains)
- `TripPhoto` 1:N (has)
- `SavedTrip` 1:N (original_trip로 참조 — 이 여행을 원본으로 스크랩한 기록들)
- `SavedTrip` 1:0..1 (copied_trip로 참조 — 이 여행이 누군가의 복사본이라면 그 스크랩 기록 하나)

---

## SavedTrip
**테이블**: `saved_trips`  
**설명**: 사용자가 다른 사용자의 공개 여행을 스크랩(찜)한 기록. 스크랩과 "내 계획으로 복사"는 별개 동작이라, 스크랩 시점엔 `copiedTrip`이 비어 있다가 사용자가 실제로 복사할 때만 채워진다.

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| user | user_id | UUID (FK) | 스크랩한 사용자 참조 |
| originalTrip | original_trip_id | UUID (FK) | 원본 여행 참조 |
| copiedTrip | copied_trip_id | UUID (FK, nullable) | 복사해서 만든 내 소유 여행 참조 — 복사 전이거나 복사본을 삭제하면 null |
| sourceType | source_type | FeedItemType (PLAN, RECORD) | 어느 카드(계획/기록)에서 스크랩했는지. 해제 후 다시 스크랩하면 그 시점 값으로 갱신 |
| savedAt | saved_at | LocalDateTime | 스크랩 일시 |

---

## TripDay
**테이블**: `trip_days`  
**설명**: 여행 계획의 일자별 구성

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| trip | trip_id | UUID (FK) | 여행 참조 |
| dayNumber | day_number | int | 여행 N일차 |
| date | date | LocalDate | 실제 날짜 |

**연관관계**
- `TripItem` 1:N (schedules)

---

## TripPhoto
**테이블**: `trip_photos`  
**설명**: 여행에 첨부된 사진

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| trip | trip_id | UUID (FK) | 여행 참조 |
| imageUrl | image_url | String | 이미지 URL |
| caption | caption | String(500) | 사진 설명 |
| uploadedAt | uploaded_at | LocalDateTime | 업로드 일시 |

---

## TripRecord
**테이블**: `trip_records`
**설명**: 계획 기반 여행 기록(후기). 계획당 1개, 내용 필수, 사진 1장 이상(`TripPhoto`)

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| trip | trip_id | UUID (FK) | 여행 참조 (unique — 계획당 기록 1개) |
| title | title | String | 기록 제목 |
| content | content | String | 후기 내용 |
| createdAt | created_at | LocalDateTime | 작성 일시 |

**연관관계**
- `TripPhoto` 1:N (has)

---

## TripItem
**테이블**: `trip_items`  
**설명**: 여행 일자에 포함된 개별 일정(장소/활동)

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| tripDay | trip_day_id | UUID (FK) | 여행 일자 참조 |
| tourApiContentId | tour_api_content_id | String | 투어 API 콘텐츠 ID |
| cachedTitle | cached_title | String | 캐시된 장소 명칭 |
| cachedImageUrl | cached_image_url | String | 캐시된 이미지 URL |
| address | address | String | 캐시된 전체 주소 (지역 라벨 파싱·상세 표시용) |
| memo | memo | String | 사용자가 남긴 메모 |
| startTime | start_time | LocalTime | 일정 시작 시간 |
| endTime | end_time | LocalTime | 일정 종료 시간 |
| orderIndex | order_index | int | 해당 날짜 내 순서 |
| petFriendly | pet_friendly | Boolean | 반려동물 동반 가능 여부 (장바구니 값 복사, null = 미확인) |

---

## TripFeedback
**테이블**: `trip_feedbacks`
**설명**: 공개된 여행계획(Trip 전체/특정 일자/특정 장소 단위)에 남기는 참견(피드백)

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| trip | trip_id | UUID (FK) | 대상 여행계획 |
| tripDay | trip_day_id | UUID (FK, nullable) | not null이면 일자 단위 피드백 |
| tripItem | trip_item_id | UUID (FK, nullable) | not null이면 장소 단위 피드백 (tripDay와 동시 설정 불가) |
| author | author_id | UUID (FK, nullable) | 작성자. **작성자 탈퇴 시 참견은 남기고 null로 익명화** (AccountDeletionService) |
| content | content | String | 참견 내용 |
| read | is_read | boolean | 여행 소유자의 열람 여부 |
| createdAt | created_at | LocalDateTime | 작성일시 |
| updatedAt | updated_at | LocalDateTime | 수정일시 |

## Notification

사용자 알림. 참견 등 다른 사용자의 행동을 **받는 사람 기준**으로 저장한다. 행위자·계획·참견 정보는 FK 없이 스냅샷으로 두어 원본이 수정·삭제돼도 알림은 남는다. 실시간 전달은 `GET /api/notifications/stream`(SSE)로 하고, 접속 중이 아니면 저장된 알림을 나중에 조회한다.

| 필드 (Java) | 컬럼 (DB) | 타입 | 설명 |
|---|---|---|---|
| id | id | UUID (PK) | 고유 식별자 |
| user | user_id | UUID (FK) | 받는 사람 (탈퇴 시 함께 삭제) |
| type | type | NotificationType | FEEDBACK(참견) / SCRAP(스크랩, feedback 관련 컬럼은 null) |
| actorId | actor_id | UUID | 행위자(참견 작성자) ID 스냅샷 |
| actorName | actor_name | String | 행위자 이름 스냅샷 |
| tripId | trip_id | UUID | 대상 계획 ID (계획 삭제 시 알림도 삭제) |
| tripTitle | trip_title | String | 계획 제목 스냅샷 |
| thumbnailUrl | thumbnail_url | String(1000) | 계획 썸네일 스냅샷 (기록 첫 사진, 없으면 null) |
| feedbackId | feedback_id | UUID | 참견 ID |
| target | target | NotificationTarget | TRIP / DAY / ITEM |
| dayNumber | day_number | Integer | DAY·ITEM 참견의 일차 |
| itemTitle | item_title | String | ITEM 참견의 장소명 |
| preview | preview | String(200) | 참견 내용 앞 60자 |
| read | is_read | boolean (default false) | 읽음 여부 |
| createdAt | created_at | LocalDateTime | 생성일시 |
