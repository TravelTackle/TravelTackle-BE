package Timeout.travel_tackle.trip.repository;

import Timeout.travel_tackle.entity.TripFeedback;
import Timeout.travel_tackle.entity.TripFeedbackLike;
import Timeout.travel_tackle.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripFeedbackLikeRepository extends JpaRepository<TripFeedbackLike, UUID> {

    boolean existsByFeedbackAndUser(TripFeedback feedback, User user);

    Optional<TripFeedbackLike> findByFeedbackAndUser(TripFeedback feedback, User user);

    // 참견 목록 조회용 — 여러 피드백의 좋아요 수를 한 번에 집계
    @Query("SELECT l.feedback.id, COUNT(l) FROM TripFeedbackLike l WHERE l.feedback.id IN :feedbackIds GROUP BY l.feedback.id")
    List<Object[]> countGroupByFeedbackIds(@Param("feedbackIds") List<UUID> feedbackIds);

    // 참견 목록 조회용 — 로그인 사용자가 좋아요를 누른 피드백 id만 추려낸다
    @Query("SELECT l.feedback.id FROM TripFeedbackLike l WHERE l.feedback.id IN :feedbackIds AND l.user.id = :userId")
    List<UUID> findLikedFeedbackIds(@Param("feedbackIds") List<UUID> feedbackIds, @Param("userId") UUID userId);

    // 참견/여행 삭제 시 자식 row 먼저 정리 (FK 제약 준수)
    @Modifying
    @Query("DELETE FROM TripFeedbackLike l WHERE l.feedback.id = :feedbackId")
    void deleteAllByFeedbackId(@Param("feedbackId") UUID feedbackId);

    @Modifying
    @Query("DELETE FROM TripFeedbackLike l WHERE l.feedback.id IN :feedbackIds")
    void deleteAllByFeedbackIds(@Param("feedbackIds") List<UUID> feedbackIds);

    // 회원탈퇴 시 남의 참견에 내가 누른 좋아요는 익명화할 의미가 없어 그냥 지운다
    @Modifying
    @Query("DELETE FROM TripFeedbackLike l WHERE l.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
