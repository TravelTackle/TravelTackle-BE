package Timeout.travel_tackle.cart.controller;
import Timeout.travel_tackle.cart.service.CartService;

import Timeout.travel_tackle.cart.service.CartService.CartItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cart-items")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "관광 콘텐츠 장바구니 API")
public class CartController {

    private final CartService cartService;

    @PostMapping
    @Operation(summary = "관광 콘텐츠 장바구니 추가")
    public ResponseEntity<CartItemResponse> add(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(cartService.add(jwt.getSubject(), request.contentId(), request.language()));
    }

    @GetMapping
    @Operation(summary = "내 장바구니 조회")
    public List<CartItemResponse> getItems(@AuthenticationPrincipal Jwt jwt) {
        return cartService.getItems(jwt.getSubject());
    }

    @DeleteMapping("/{cartItemId}")
    @Operation(summary = "장바구니 항목 삭제")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID cartItemId
    ) {
        cartService.remove(jwt.getSubject(), cartItemId);
        return ResponseEntity.noContent().build();
    }

    public record AddCartItemRequest(@NotBlank String contentId, String language) {
    }
}
