package Timeout.travel_tackle.auth.repository;

import Timeout.travel_tackle.entity.RefreshToken;
import Timeout.travel_tackle.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // clearAutomatically = true는 이 쿼리가 실행된 트랜잭션의 영속성 컨텍스트 전체를 비운다
    // (RefreshToken뿐 아니라 같은 트랜잭션에서 로드된 User 등 다른 엔티티도 detach됨).
    // 호출 직후에 이미 로드된 엔티티를 추가로 조회/수정하는 코드를 넣을 경우, 최신 상태를 보려면
    // 반드시 다시 조회해야 한다 — detach된 인스턴스를 계속 쓰면 변경사항이 저장되지 않는다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken r set r.revokedAt = :now where r.user = :user and r.revokedAt is null")
    int revokeAllActiveByUser(@Param("user") User user, @Param("now") LocalDateTime now);

    // 회원탈퇴 시 RefreshToken row 자체를 삭제 (revoke만으로는 user FK 제약이 남아 계정 삭제가 막힘)
    void deleteAllByUser(User user);
}
