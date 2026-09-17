package Timeout.travel_tackle.trip.repository;

import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.TripRecord;
import Timeout.travel_tackle.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRecordRepository extends JpaRepository<TripRecord, UUID> {
    Optional<TripRecord> findByTrip(Trip trip);

    boolean existsByTrip(Trip trip);

    /**
     * 추천 탭(기록 추천)용 — 공개된 여러 계획의 기록을 작성자/계획과 함께 한 번에 조회.
     */
    @Query("select r from TripRecord r join fetch r.trip t join fetch t.user "
            + "where t.published = true and t.id in :tripIds")
    List<TripRecord> findPublishedByTripIdInWithTripAndUser(@Param("tripIds") Collection<UUID> tripIds);

    /**
     * 보관함(SavedTrip) 응답 조립용 — 공개 여부와 무관하게 여러 계획의 기록을 한 번에 조회한다.
     * sourceType=RECORD로 스크랩한 계획이라도 그 사이 기록이 지워졌을 수 있어(폴백 판단에 씀).
     */
    @Query("select r from TripRecord r where r.trip.id in :tripIds")
    List<TripRecord> findAllByTripIdIn(@Param("tripIds") Collection<UUID> tripIds);

    // 공개 프로필의 "기록 수" — 피드에 노출되는 기록(계획이 공개된 경우)만 센다
    // 파생 쿼리의 밑줄 표기(Trip_User)는 ArchUnit camelCase 규칙에 걸려 JPQL 로 명시한다
    @Query("select count(r) from TripRecord r where r.trip.user = :user and r.trip.published = true")
    long countPublishedByOwner(@Param("user") User user);
}
