package Timeout.travel_tackle.notification.repository;

import Timeout.travel_tackle.entity.Notification;
import Timeout.travel_tackle.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    // 벌크 UPDATE 뒤 같은 트랜잭션에서 조회해도 최신 값이 보이도록 영속성 컨텍스트를 비운다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    int markAllReadByUserId(@Param("userId") UUID userId);

    // 삭제된 행이 같은 트랜잭션의 이후 조회(미읽음 수)에 남아 보이지 않도록 영속성 컨텍스트를 비운다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    void deleteAllByUser(User user);

    void deleteAllByTripId(UUID tripId);
}
