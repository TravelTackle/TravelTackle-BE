package Timeout.travel_tackle.trip.repository;

import Timeout.travel_tackle.entity.SavedTrip;
import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedTripRepository extends JpaRepository<SavedTrip, UUID> {
    boolean existsByUserAndOriginalTrip(User user, Trip originalTrip);

    Optional<SavedTrip> findByIdAndUser(UUID id, User user);

    void deleteAllByOriginalTrip(Trip originalTrip);

    void deleteAllByUser(User user);

    @Query("select s from SavedTrip s join fetch s.originalTrip t join fetch t.user "
            + "where s.user = :user order by s.savedAt desc")
    List<SavedTrip> findAllWithOriginalByUser(@Param("user") User user);

    @Query("select s.originalTrip.id, count(s) from SavedTrip s "
            + "where s.originalTrip.id in :tripIds group by s.originalTrip.id")
    List<Object[]> countGroupByOriginalTripIds(@Param("tripIds") List<UUID> tripIds);

    // row[0] = originalTrip.id, row[1] = savedTrip.id — 피드 카드에서 북마크를 토글(스크랩 해제)하려면
    // 어떤 SavedTrip을 지울지 알아야 하므로 여부(boolean)가 아니라 id 자체를 돌려준다.
    @Query("select s.originalTrip.id, s.id from SavedTrip s where s.user = :user and s.originalTrip.id in :tripIds")
    List<Object[]> findSavedTripIdsByOriginalTripIds(@Param("user") User user, @Param("tripIds") List<UUID> tripIds);

    // Trip 삭제 전 FK 위반을 막기 위해, 그 Trip을 복사본으로 참조 중인 SavedTrip의 참조만 끊는다
    // (스크랩 이력 자체는 남긴다 — 예: 사용자가 복사본만 지운 경우 "복사하기" 버튼이 다시 활성화됨).
    @Modifying
    @Query("update SavedTrip s set s.copiedTrip = null where s.copiedTrip = :copiedTrip")
    void clearCopiedTripReference(@Param("copiedTrip") Trip copiedTrip);
}
