package Timeout.travel_tackle.cart.repository;

import Timeout.travel_tackle.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    boolean existsByUserIdAndTourApiContentId(UUID userId, String tourApiContentId);

    List<CartItem> findAllByUserIdOrderByAddedAtDesc(UUID userId);

    Optional<CartItem> findByIdAndUserId(UUID id, UUID userId);

    void deleteAllByUserId(UUID userId);

    @Query("select distinct c.tourApiContentId from CartItem c where c.petFriendly is null")
    List<String> findContentIdsWithUnknownPetFriendly();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CartItem c set c.petFriendly = :petFriendly where c.tourApiContentId = :contentId and c.petFriendly is null")
    int fillPetFriendly(@Param("contentId") String contentId, @Param("petFriendly") boolean petFriendly);
}
