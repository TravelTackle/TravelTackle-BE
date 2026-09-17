package Timeout.travel_tackle.cart.repository;

import Timeout.travel_tackle.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    boolean existsByUserIdAndTourApiContentId(UUID userId, String tourApiContentId);

    List<CartItem> findAllByUserIdOrderByAddedAtDesc(UUID userId);

    Optional<CartItem> findByIdAndUserId(UUID id, UUID userId);

    void deleteAllByUserId(UUID userId);
}
