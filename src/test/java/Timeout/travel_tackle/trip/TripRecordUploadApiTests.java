package Timeout.travel_tackle.trip;

import Timeout.travel_tackle.auth.jwt.service.JwtService;
import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.image.service.ImageStorageService;
import Timeout.travel_tackle.trip.dto.CreateTripRequest;
import Timeout.travel_tackle.trip.service.TripService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// multipart 필드가 TripRecordUploadRequest 로 바인딩되는지 HTTP 레벨에서 확인한다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TripRecordUploadApiTests {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired TripService tripService;
    @Autowired JwtService jwtService;

    @MockitoBean ImageStorageService imageStorageService;

    @Test
    void createsRecordFromMultipartForm() throws Exception {
        User owner = userRepository.save(new User("api@upload.test", "주인", "KR"));
        LocalDate date = LocalDate.of(2026, 7, 1);
        UUID tripId = tripService.createTrip(owner.getId(), new CreateTripRequest("여행", date, date)).id();
        Cookie accessCookie = new Cookie("access_token", jwtService.createAccessToken(owner));
        when(imageStorageService.upload(eq(owner.getId()), any(MultipartFile.class)))
                .thenReturn("https://cdn.test/images/1.jpg", "https://cdn.test/images/2.jpg", "https://cdn.test/images/3.jpg");

        mockMvc.perform(multipart("/api/trips/" + tripId + "/record")
                        .file(new MockMultipartFile("photos", "a.jpg", "image/jpeg", new byte[]{1}))
                        .file(new MockMultipartFile("photos", "b.jpg", "image/jpeg", new byte[]{2}))
                        .param("title", "제목")
                        .param("content", "내용")
                        .param("captions", "첫 장", "둘째 장")
                        .cookie(accessCookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.photos[0].imageUrl").value("https://cdn.test/images/1.jpg"))
                .andExpect(jsonPath("$.photos[0].caption").value("첫 장"))
                .andExpect(jsonPath("$.photos[1].caption").value("둘째 장"));

        mockMvc.perform(multipart(PATCH, "/api/trips/" + tripId + "/record")
                        .file(new MockMultipartFile("photos", "c.jpg", "image/jpeg", new byte[]{3}))
                        .param("title", "수정")
                        .param("content", "수정 내용")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정"))
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].imageUrl").value("https://cdn.test/images/3.jpg"));
    }

    @Test
    void rejectsMultipartWithoutPhotos() throws Exception {
        User owner = userRepository.save(new User("api2@upload.test", "주인", "KR"));
        LocalDate date = LocalDate.of(2026, 7, 1);
        UUID tripId = tripService.createTrip(owner.getId(), new CreateTripRequest("여행", date, date)).id();
        Cookie accessCookie = new Cookie("access_token", jwtService.createAccessToken(owner));

        mockMvc.perform(multipart("/api/trips/" + tripId + "/record")
                        .param("title", "제목")
                        .param("content", "내용")
                        .cookie(accessCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }
}
