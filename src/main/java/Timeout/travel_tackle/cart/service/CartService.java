package Timeout.travel_tackle.cart.service;
import Timeout.travel_tackle.cart.repository.CartItemRepository;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.entity.CartItem;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.global.util.UuidConverter;
import Timeout.travel_tackle.tour.dto.TourDtos.ContentDetail;
import Timeout.travel_tackle.tour.service.TourService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final TourService tourService;

    @Transactional
    public CartItemResponse add(String subject, String contentId) {
        UUID userId = parseUserId(subject);
        if (cartItemRepository.existsByUserIdAndTourApiContentId(userId, contentId)) {
            throw new CustomException(ErrorCode.CART_ITEM_ALREADY_EXISTS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHENTICATED));
        ContentDetail content = tourService.getContentDetail(contentId);

        CartItem cartItem = cartItemRepository.save(new CartItem(
                user,
                content.contentId(),
                content.title(),
                content.imageUrl(),
                content.areaCode(),
                content.contentTypeId(),
                content.lclsSystm1(),
                content.lclsSystm2(),
                content.lclsSystm3()
        ));
        return CartItemResponse.from(cartItem);
    }

    @Transactional(readOnly = true)
    public List<CartItemResponse> getItems(String subject) {
        UUID userId = parseUserId(subject);
        return cartItemRepository.findAllByUserIdOrderByAddedAtDesc(userId).stream()
                .map(CartItemResponse::from)
                .toList();
    }

    @Transactional
    public CartItemResponse addFromCachedData(UUID userId, String tourApiContentId,
                                              String cachedTitle, String cachedImageUrl, String cachedRegionCode) {
        if (cartItemRepository.existsByUserIdAndTourApiContentId(userId, tourApiContentId)) {
            throw new CustomException(ErrorCode.CART_ITEM_ALREADY_EXISTS);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHENTICATED));
        CartItem cartItem = cartItemRepository.save(
                new CartItem(user, tourApiContentId, cachedTitle, cachedImageUrl, cachedRegionCode,
                        null, null, null, null));
        return CartItemResponse.from(cartItem);
    }

    @Transactional
    public void remove(String subject, UUID cartItemId) {
        UUID userId = parseUserId(subject);
        CartItem cartItem = cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CART_ITEM_NOT_FOUND));
        cartItemRepository.delete(cartItem);
    }

    private UUID parseUserId(String subject) {
        return UuidConverter.fromSubject(subject);
    }

    public record CartItemResponse(
            UUID id,
            String contentId,
            String title,
            String imageUrl,
            String areaCode,
            String contentTypeId,
            LocalDateTime addedAt
    ) {
        private static CartItemResponse from(CartItem item) {
            return new CartItemResponse(
                    item.getId(),
                    item.getTourApiContentId(),
                    item.getCachedTitle(),
                    item.getCachedImageUrl(),
                    item.getCachedRegionCode(),
                    item.getCachedContentTypeId(),
                    item.getAddedAt()
            );
        }
    }
}
