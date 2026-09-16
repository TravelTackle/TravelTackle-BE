package Timeout.travel_tackle.notification;

import Timeout.travel_tackle.auth.jwt.service.JwtService;
import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.cart.repository.CartItemRepository;
import Timeout.travel_tackle.entity.CartItem;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.notification.sse.NotificationSseRegistry;
import Timeout.travel_tackle.trip.dto.AddTripItemRequest;
import Timeout.travel_tackle.trip.dto.CreateFeedbackRequest;
import Timeout.travel_tackle.trip.dto.CreateTripRequest;
import Timeout.travel_tackle.trip.service.TripFeedbackService;
import Timeout.travel_tackle.trip.service.TripService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationApiTests {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired TripService tripService;
    @Autowired TripFeedbackService feedbackService;
    @Autowired JwtService jwtService;
    @Autowired NotificationSseRegistry sseRegistry;

    @Test
    void listUnreadCountAndReadEndpointsWorkTogether() throws Exception {
        User owner = userRepository.save(new User("api-owner@noti.test", "주인", "KR"));
        User reviewer = userRepository.save(new User("api-reviewer@noti.test", "리뷰어", "KR"));
        UUID tripId = publishedTrip(owner);
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("좋네요", null, null, List.of()));
        Cookie cookie = new Cookie("access_token", jwtService.createAccessToken(owner));

        mockMvc.perform(get("/api/notifications/unread-count").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        MvcResult list = mockMvc.perform(get("/api/notifications").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.content[0].type").value("FEEDBACK"))
                .andExpect(jsonPath("$.content[0].actor.name").value("리뷰어"))
                .andExpect(jsonPath("$.content[0].trip.id").value(tripId.toString()))
                .andExpect(jsonPath("$.content[0].feedback.target").value("TRIP"))
                .andExpect(jsonPath("$.content[0].feedback.preview").value("좋네요"))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(list.getResponse().getContentAsString(), "$.content[0].id");

        mockMvc.perform(patch("/api/notifications/" + id + "/read").cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/unread-count").cookie(cookie)).andExpect(jsonPath("$.unreadCount").value(0));

        // 다른 사용자의 알림은 읽음 처리 불가
        Cookie reviewerCookie = new Cookie("access_token", jwtService.createAccessToken(reviewer));
        mockMvc.perform(patch("/api/notifications/" + id + "/read").cookie(reviewerCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTI_001"));
        // 전체 읽음: 내 미읽음 2개는 0이 되고, 다른 사용자의 미읽음은 그대로
        User other = userRepository.save(new User("api-other@noti.test", "제3자", "KR"));
        UUID otherTripId = publishedTrip(other);
        feedbackService.create(reviewer.getId(), otherTripId, new CreateFeedbackRequest("남의 것", null, null, List.of()));
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("둘", null, null, List.of()));
        feedbackService.create(reviewer.getId(), tripId, new CreateFeedbackRequest("셋", null, null, List.of()));
        mockMvc.perform(get("/api/notifications/unread-count").cookie(cookie)).andExpect(jsonPath("$.unreadCount").value(2));

        mockMvc.perform(patch("/api/notifications/read-all").cookie(cookie)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications/unread-count").cookie(cookie)).andExpect(jsonPath("$.unreadCount").value(0));
        Cookie otherCookie = new Cookie("access_token", jwtService.createAccessToken(other));
        mockMvc.perform(get("/api/notifications/unread-count").cookie(otherCookie)).andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void streamOpensSseConnectionForAuthenticatedUser() throws Exception {
        User owner = userRepository.save(new User("sse-owner@noti.test", "주인", "KR"));
        Cookie cookie = new Cookie("access_token", jwtService.createAccessToken(owner));

        MvcResult result = mockMvc.perform(get("/api/notifications/stream").cookie(cookie))
                .andExpect(request().asyncStarted())
                .andReturn();
        try {
            assertTrue(result.getResponse().getContentType().startsWith("text/event-stream"));
            assertTrue(result.getResponse().getContentAsString().contains("event:connected"));
            assertEquals(1, sseRegistry.connectionCount(owner.getId()));
        } finally {
            // 클라이언트가 끊은 것처럼 async 를 완료시키면 emitter 콜백이 레지스트리에서 연결을 제거해야 한다
            result.getRequest().getAsyncContext().complete();
        }
        assertEquals(0, sseRegistry.connectionCount(owner.getId()));

        mockMvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
    }

    private UUID publishedTrip(User owner) {
        LocalDate date = LocalDate.of(2026, 7, 1);
        UUID tripId = tripService.createTrip(owner.getId(), new CreateTripRequest("여행", date, date)).id();
        UUID dayId = tripService.getTripDetail(owner.getId(), tripId).days().getFirst().id();
        CartItem cart = cartItemRepository.save(new CartItem(owner, "1", "KorService2", "장소", null, "1", null, null, null, null));
        tripService.addTripItem(owner.getId(), tripId, dayId, new AddTripItemRequest(cart.getId(), null, null));
        tripService.publishTrip(owner.getId(), tripId);
        return tripId;
    }
}
