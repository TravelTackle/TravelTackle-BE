package Timeout.travel_tackle.auth.repository;

import Timeout.travel_tackle.entity.Enum.AuthProvider;
import Timeout.travel_tackle.entity.UserAuthProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<UserAuthProvider> findByProviderAndProviderUserId(
            AuthProvider provider,
            String providerUserId
    );

    List<UserAuthProvider> findAllByUserId(UUID userId);

    void deleteAllByUserId(UUID userId);
}
