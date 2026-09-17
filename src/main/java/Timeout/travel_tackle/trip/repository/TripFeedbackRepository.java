package Timeout.travel_tackle.trip.repository;

import Timeout.travel_tackle.entity.TripFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripFeedbackRepository extends JpaRepository<TripFeedback, UUID> {

    // Trip 전체 피드백 (day/item 모두 null)
    Page<TripFeedback> findAllByTripIdAndTripDayIsNullAndTripItemIsNull(UUID tripId, Pageable pageable);

    // 레벨 구분 없이 전체 피드백
    Page<TripFeedback> findAllByTripId(UUID tripId, Pageable pageable);

    // TripDay 단위 피드백
    Page<TripFeedback> findAllByTripDayId(UUID tripDayId, Pageable pageable);

    // TripItem 단위 피드백
    Page<TripFeedback> findAllByTripItemId(UUID tripItemId, Pageable pageable);

    Optional<TripFeedback> findByIdAndTripId(UUID id, UUID tripId);

    boolean existsByTripIdAndAuthorId(UUID tripId, UUID authorId);

    // 모아보기: 내 Trip 목록의 피드백 수 집계
    @Query("SELECT f.trip.id, COUNT(f) FROM TripFeedback f WHERE f.trip.id IN :tripIds GROUP BY f.trip.id")
    List<Object[]> countGroupByTripIds(@Param("tripIds") List<UUID> tripIds);

    // 모아보기: 미읽음 피드백 수 집계
    @Query("SELECT f.trip.id, COUNT(f) FROM TripFeedback f WHERE f.trip.id IN :tripIds AND f.read = false GROUP BY f.trip.id")
    List<Object[]> countUnreadGroupByTripIds(@Param("tripIds") List<UUID> tripIds);

    // 소유자가 피드백 목록 열람 시 일괄 읽음 처리
    @Modifying
    @Query("UPDATE TripFeedback f SET f.read = true WHERE f.trip.id = :tripId AND f.read = false")
    void markAllReadByTripId(@Param("tripId") UUID tripId);

    // TripItem 삭제 시 trip_item_id SET NULL
    @Modifying
    @Query("UPDATE TripFeedback f SET f.tripItem = null WHERE f.tripItem.id = :itemId")
    void clearTripItemById(@Param("itemId") UUID itemId);

    // TripDay 삭제 시 trip_day_id SET NULL
    @Modifying
    @Query("UPDATE TripFeedback f SET f.tripDay = null WHERE f.tripDay.id = :dayId")
    void clearTripDayById(@Param("dayId") UUID dayId);

    // deleteTrip / bulkNullify 용
    @Query("SELECT f.id FROM TripFeedback f WHERE f.trip.id = :tripId")
    List<UUID> findIdsByTripId(@Param("tripId") UUID tripId);

    void deleteAllByTripId(UUID tripId);

    // 회원탈퇴 시 남의 여행계획에 남긴 참견은 삭제하지 않고 author만 익명화
    @Modifying
    @Query("UPDATE TripFeedback f SET f.author = null WHERE f.author.id = :authorId")
    void anonymizeByAuthorId(@Param("authorId") UUID authorId);

    // 모아보기: 최근 피드백 시각
    @Query("SELECT f.trip.id, MAX(f.createdAt) FROM TripFeedback f WHERE f.trip.id IN :tripIds GROUP BY f.trip.id")
    List<Object[]> findLatestCreatedAtGroupByTripIds(@Param("tripIds") List<UUID> tripIds);
}
