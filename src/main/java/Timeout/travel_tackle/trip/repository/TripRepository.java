package Timeout.travel_tackle.trip.repository;

import Timeout.travel_tackle.entity.Trip;
import Timeout.travel_tackle.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findAllByUserOrderByCreatedAtDesc(User user);

    @Query("select t from Trip t join fetch t.user where t.published = true and t.id in :ids")
    List<Trip> findPublishedByIdInWithUser(@Param("ids") Collection<UUID> ids);

    @Query(value = "select t from Trip t join fetch t.user where t.published = true",
            countQuery = "select count(t) from Trip t where t.published = true")
    Page<Trip> findPublishedWithUser(Pageable pageable);

    // 인기 점수 = 참견(TripFeedback) 수 + 스크랩(SavedTrip) 수, 동점은 최신순 — 정렬이 쿼리에 고정되므로 pageable 은 unsorted 로 넘긴다
    @Query(value = "select t from Trip t join fetch t.user where t.published = true "
            + "order by ((select count(f) from TripFeedback f where f.trip = t) "
            + "+ (select count(s) from SavedTrip s where s.originalTrip = t)) desc, t.createdAt desc",
            countQuery = "select count(t) from Trip t where t.published = true")
    Page<Trip> findPublishedWithUserOrderByPopularity(Pageable pageable);

    @Query("select t from Trip t join fetch t.user where t.id = :id and t.published = true")
    Optional<Trip> findPublishedDetailById(@Param("id") UUID id);

    long countByUserAndPublishedTrue(User user);

    // 특정 사용자의 공개 프로필(마이페이지 외부 열람)용 — 그 사람의 공개 계획만 페이지네이션
    @Query(value = "select t from Trip t join fetch t.user where t.published = true and t.user = :user",
            countQuery = "select count(t) from Trip t where t.published = true and t.user = :user")
    Page<Trip> findPublishedByUser(@Param("user") User user, Pageable pageable);

    @Query(value = "select t from Trip t join fetch t.user where t.published = true and t.user = :user "
            + "order by ((select count(f) from TripFeedback f where f.trip = t) "
            + "+ (select count(s) from SavedTrip s where s.originalTrip = t)) desc, t.createdAt desc",
            countQuery = "select count(t) from Trip t where t.published = true and t.user = :user")
    Page<Trip> findPublishedByUserOrderByPopularity(@Param("user") User user, Pageable pageable);
}
