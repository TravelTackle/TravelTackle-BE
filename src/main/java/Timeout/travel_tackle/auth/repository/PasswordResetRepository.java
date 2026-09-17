package Timeout.travel_tackle.auth.repository;

import Timeout.travel_tackle.entity.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {

    Optional<PasswordReset> findTopByEmailOrderByCreatedAtDesc(String email);

    long countByEmailAndCreatedAtAfter(String email, LocalDateTime createdAt);

    // REQUIRES_NEW: 실패 횟수는 confirmReset()이 곧바로 예외를 던져 자신의 트랜잭션을 롤백하더라도
    // 반드시 남아야 하므로, 호출자의 트랜잭션과 분리된 별도 트랜잭션에서 즉시 커밋한다.
    // 원자적 UPDATE라 동시 요청에 의한 lost update도 없다.
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update PasswordReset r set r.attempts = r.attempts + 1 where r.id = :id")
    void incrementAttempts(@Param("id") UUID id);
}
