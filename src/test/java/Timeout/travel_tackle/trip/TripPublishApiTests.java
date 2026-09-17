package Timeout.travel_tackle.trip;

import Timeout.travel_tackle.auth.jwt.service.JwtService;
import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.cart.repository.CartItemRepository;
import Timeout.travel_tackle.entity.CartItem;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.trip.dto.AddTripItemRequest;
import Timeout.travel_tackle.trip.dto.CreateTripRequest;
import Timeout.travel_tackle.trip.dto.TripDetailResponse;
import Timeout.travel_tackle.trip.service.TripService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 공개 전환 거부 시 응답 message 에 비어 있는 일차가 그대로 실리는지 HTTP 레벨에서 확인
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TripPublishApiTests {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired TripService tripService;
    @Autowired JwtService jwtService;

    @Test
    void publishWithEmptyDaysReturns400WithDayNumbersInMessage() throws Exception {
        User owner = userRepository.save(new User("publish-api@example.com", "주인", "KR"));
        LocalDate start = LocalDate.of(2026, 7, 1);
        UUID tripId = tripService.createTrip(owner.getId(), new CreateTripRequest("여행", start, start.plusDays(2))).id();
        TripDetailResponse detail = tripService.getTripDetail(owner.getId(), tripId);
        CartItem cartItem = cartItemRepository.save(new CartItem(owner, "1", "경복궁", null, "1", null, null, null, null));
        tripService.addTripItem(owner.getId(), tripId, detail.days().get(1).id(), new AddTripItemRequest(cartItem.getId(), null, null));
        Cookie accessCookie = new Cookie("access_token", jwtService.createAccessToken(owner));

        mockMvc.perform(patch("/api/trips/" + tripId + "/publish").cookie(accessCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_022"))
                .andExpect(jsonPath("$.message").value(containsString("Day 1, Day 3")));
    }
}
